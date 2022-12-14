package com.alan10607.leaf.service.impl;

import com.alan10607.leaf.constant.CountType;
import com.alan10607.leaf.dao.LeafDAO;
import com.alan10607.leaf.dto.LeafDTO;
import com.alan10607.leaf.model.Leaf;
import com.alan10607.leaf.service.ViewService;
import com.alan10607.leaf.util.RedisKeyUtil;
import com.alan10607.leaf.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ViewServiceImpl implements ViewService {
    private final RedisTemplate redisTemplate;
    private final RedissonClient redisson;
    private final RedisKeyUtil redisKeyUtil;
    private final LeafDAO leafDAO;
    private final TimeUtil timeUtil;
    private final static int EXPIRE_TIME = 3600;//leaf hash過期時間

    /**
     * 從Redis查詢count, 若不存在則去DB查
     * @param leafDTO
     * @return
     * @throws Exception
     */
    public LeafDTO findCountFromRedis(LeafDTO leafDTO) throws Exception {
        String leafName = leafDTO.getLeafName();
        if(Strings.isBlank(leafName)) throw new IllegalStateException("LeafName can't be blank");

        //先檢查是否存在redis
        String hashKey = redisKeyUtil.leafKey(leafName);
        if(!redisTemplate.hasKey(hashKey)){
            if(!findCountFromDB(leafName))//沒有就去DB查
                throw new IllegalStateException("FindCountFromDB is busy, try later");
        }

        //已確定存在, 查詢redis
        RReadWriteLock lock = redisson.getReadWriteLock(redisKeyUtil.rwLock(leafName));
        Map<String, Number> map = new HashMap<>();//設置為Number, 因為超過2^31-1時會是Long, 否則會是Integer
        try {
            lock.readLock().lock();
            map = redisTemplate.opsForHash().entries(hashKey);
        }catch (Exception e){
            log.error("", e);
        }finally {
            lock.readLock().unlock();
        }

        leafDTO.setGood(map.get("good").longValue());
        leafDTO.setBad(map.get("bad").longValue());
        return leafDTO;
    }

    /**
     * 從Redis增加count, 若不存在則去DB查然後增加
     * @param leafDTO
     * @throws Exception
     */
    public LeafDTO countIncr(LeafDTO leafDTO) throws Exception {
        String leafName = leafDTO.getLeafName();
        if(Strings.isBlank(leafName)) throw new IllegalStateException("LeafName can't be blank");

        CountType countType = getCountType(leafDTO.getVoteFor());
        if(countType == CountType.UNDEFINED) throw new IllegalStateException("VoteFor is not defined");

        //先檢查是否存在redis
        String hashKey = redisKeyUtil.leafKey(leafName);
        if(!redisTemplate.hasKey(hashKey)){
            if(!findCountFromDB(leafName))//沒有就去DB查
                throw new IllegalStateException("FindCountFromDB is busy, try later");
        }

        long res = -1;
        RReadWriteLock lock = redisson.getReadWriteLock(redisKeyUtil.rwLock(leafName));
        try {
            lock.writeLock().lock();
            res = redisTemplate.opsForHash().increment(hashKey, countType.getField(), 1);

            //Cache Avalanche, 防止緩存雪崩, 設定亂數過期時間
            redisTemplate.expire(hashKey, redisKeyUtil.getRanExp(EXPIRE_TIME), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            lock.writeLock().unlock();
        }
        return countType.getResLeafDTO(leafDTO, res);
    }

    /**
     * 到DB查詢後放入Redis, 採用分布式鎖防止失效時惡意重複查詢, 若鎖獲取失敗回傳false, 否則true
     * @param leafName
     * @return
     * @throws Exception
     */
    public boolean findCountFromDB(String leafName) throws Exception {
        RLock lock = redisson.getLock(redisKeyUtil.LOCK_FIND_COUNT_FROM_DB);
        Boolean tryLock = false;
        try{
            tryLock = lock.tryLock();
            if(tryLock){
                //Cache Penetration, 防止緩存穿透, 查不到先設為0
                Optional<Leaf> leaf = leafDAO.findByLeafName(leafName);
                Map<String, Long> fields = leaf
                        .map(l -> Map.of("good", l.getGood(), "bad", l.getBad()))
                        .orElse(Map.of("good", 0L, "bad", 0L));

                if(leaf.isEmpty())
                    log.error("Find leaf count from DB but leafName:{} was not found", leafName);

                String hashKey = redisKeyUtil.leafKey(leafName);
                redisTemplate.opsForHash().putAll(hashKey, fields);
                log.info("FindCountFromDB for leafName:{} succeeded", leafName);
            }else{
                //Hotspot Invalid, 防止緩存擊穿, 若已經有查詢, 就先等一下再去Redis看
                Thread.sleep(1000);
                log.error("FindCountFromDB for leafName:{} was started, so sleep for 1 sec", leafName);
            }
        } catch (Exception e) {
            log.error("Find leaf count from DB failed", e);
            throw new Exception(e);
        } finally {
            if(lock.isLocked() && lock.isHeldByCurrentThread()){
                lock.unlock();//已鎖定且為當前線程的鎖, 才解鎖
            }
        }
        return tryLock;
    }

    /**
     * 透過批次將Redis資料存入DB, 採用分布式鎖避免批次衝突
     * @return
     * @throws Exception
     */
    public boolean saveCountToDB() throws Exception {
        RLock lock = redisson.getLock(redisKeyUtil.LOCK_SAVE_COUNT_TO_DB);
        Boolean tryLock = false;
        try{
            tryLock = lock.tryLock();
            if(tryLock) {
                //1 查詢要更新的key, 如果沒有就到DB查
                String nameJoin = (String) redisTemplate.opsForValue().get(redisKeyUtil.SYSTEM_DB_LEAF_NAME);
                List<String> nameList = nameJoin == null ? findAllLeafNameFromDB() : Arrays.asList(nameJoin.split(","));

                //2 從Redis中取出要更新的內容, 沒用的內容就讓他自然過期
                Map<String, Map<String, Number>> updateMap = new HashMap<>();
                for (String leafName : nameList) {
                    String hashKey = redisKeyUtil.leafKey(leafName);
                    if (redisTemplate.hasKey(hashKey)) {
                        RReadWriteLock rwLock = redisson.getReadWriteLock(redisKeyUtil.rwLock(leafName));
                        try {
                            rwLock.readLock().lock();
                            updateMap.put(leafName, redisTemplate.opsForHash().entries(hashKey));
                        } catch (Exception e) {
                            log.error("", e);
                        } finally {
                            rwLock.readLock().unlock();
                        }
                    }
                }

                //3 更新DB
                LocalDateTime now = timeUtil.now();
                for (Map.Entry<String, Map<String, Number>> entry : updateMap.entrySet()) {
                    String leafName = entry.getKey();
                    Map<String, Number> counts = entry.getValue();
                    long good = counts.get("good").longValue();
                    long bad = counts.get("bad").longValue();
                    leafDAO.updateCounts(good, bad, now, leafName);
                }
            }else{
                log.error("SaveCountToDB for was started, so skip this time");
            }
        } catch (Exception e) {
            log.error("Save leaf count from DB failed", e);
            throw new Exception(e);
        } finally {
            if(lock.isLocked() && lock.isHeldByCurrentThread()){
                lock.unlock();//已鎖定且為當前線程的鎖, 才解鎖
            }
        }
        return tryLock;
    }

    /**
     * 轉換voteFor為good或bad
     * @param voteFor
     * @return
     */
    private CountType getCountType(String voteFor) {
        for (CountType type : CountType.values()) {
            if(type.getField().equals(voteFor))
                return type;
        }
        return CountType.UNDEFINED;
    }

    /**
     * 到DB抓取所有leafName放到redis, 用於批次定期更新
     * @return
     */
    public List<String> findAllLeafNameFromDB(){
        List<String> nameList = leafDAO.findLeafName();
        String allName = nameList.stream().collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(redisKeyUtil.SYSTEM_DB_LEAF_NAME, allName);
        return nameList;
    }

}
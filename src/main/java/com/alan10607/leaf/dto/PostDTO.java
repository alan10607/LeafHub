package com.alan10607.leaf.dto;

import com.alan10607.leaf.constant.StatusType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {
    private String id;
    private String title;
    private Integer contNum;
    private StatusType status;
    private LocalDateTime updateDate;
    private LocalDateTime createDate;
    private Integer no;
    private String author;
    private String authorName;
    private String word;
    private Long likes;
    private Boolean isUserLike;
    private List<PostDTO> contList;
    private List<String> idList;
    private String userId;
    private String userName;
    private Boolean success;
    private String imgBase64;
    private String imgUrl;

    /* --- for Article --- */
    public PostDTO(String id,
                   String title,
                   Integer contNum,
                   StatusType status,
                   LocalDateTime updateDate,
                   LocalDateTime createDate) {
        this.id = id;
        this.title = title;
        this.contNum = contNum;
        this.status = status;
        this.createDate = createDate;
        this.updateDate = updateDate;
    }

    public PostDTO(String id,
                   StatusType status) {
        this.id = id;
        this.status = status;
    }

    /* --- for Content --- */
    public PostDTO(String id,
                   Integer no,
                   String author,
                   String word,
                   Long likes,
                   StatusType status,
                   LocalDateTime updateDate,
                   LocalDateTime createDate,
                   Boolean isUserLike,
                   String authorName) {
        this.id = id;
        this.status = status;
        this.createDate = createDate;
        this.no = no;
        this.author = author;
        this.word = word;
        this.likes = likes;
        this.updateDate = updateDate;
        this.isUserLike = isUserLike;
        this.authorName = authorName;
    }

    public PostDTO(String id,
                   Integer no,
                   String author,
                   String word,
                   Long likes,
                   StatusType status,
                   LocalDateTime updateDate,
                   LocalDateTime createDate) {
        this.id = id;
        this.status = status;
        this.createDate = createDate;
        this.no = no;
        this.author = author;
        this.word = word;
        this.likes = likes;
        this.updateDate = updateDate;
    }

    public PostDTO(String id,
                   Integer no,
                   StatusType status) {
        this.id = id;
        this.status = status;
        this.no = no;
    }

}
package com.alan10607.leaf.model;

import com.alan10607.leaf.constant.ArtStatusType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int contNum;

    @Column(nullable = false)
    private ArtStatusType status;

    @Column(nullable = false)
    private LocalDateTime updateDate;

    @Column(nullable = false)
    private LocalDateTime createDate;

}

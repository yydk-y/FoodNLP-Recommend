package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话记录实体类
 */
@Data
@TableName("chat_record")
public class ChatRecord {
    
    /** 对话ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long chatId;
    
    /** 关联用户ID */
    private Long userId;
    
    /** 用户自然语言输入内容 */
    private String userInput;
    
    /** BERT识别的用户意图 */
    private String bertIntent;
    
    /** BERT抽取的实体（JSON） */
    private String bertExtract;
    
    /** 系统回复内容 */
    private String systemReply;
    
    /** 对话时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime chatTime;
    
    /** 对话会话ID */
    private String sessionId;
}
package com.paradm.chatgpt.entity.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Data;

/**
 * 描述：
 *
 * @author https:www.unfbx.com
 *  2023-02-15
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteResponse implements Serializable {
    private String id;
    private String object;
    private boolean deleted;
}

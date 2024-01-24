package com.paradm.chatgpt.entity.images;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 描述：
 *
 * @author https:www.unfbx.com
 *  2023-02-15
 */
@AllArgsConstructor
@Getter
public enum ResponseFormat implements Serializable {
    URL("url"),
    B64_JSON("b64_json"),
    ;

    private final String name;
}

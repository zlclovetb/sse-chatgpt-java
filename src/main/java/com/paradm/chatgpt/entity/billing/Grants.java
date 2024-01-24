package com.paradm.chatgpt.entity.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 描述：
 *
 * @author https:www.unfbx.com
 * @since 2023-03-18
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Grants {
    private String object;
    @JsonProperty("data")
    private List<Datum> data;
}

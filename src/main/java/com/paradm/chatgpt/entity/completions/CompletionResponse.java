package com.paradm.chatgpt.entity.completions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paradm.chatgpt.entity.common.Choice;
import com.paradm.chatgpt.entity.common.OpenAiResponse;
import com.paradm.chatgpt.entity.common.Usage;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 描述： 答案类
 *
 * @author https:www.unfbx.com
 *  2023-02-11
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompletionResponse extends OpenAiResponse implements Serializable {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    private String warning;
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
}

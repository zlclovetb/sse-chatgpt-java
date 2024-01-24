package com.paradm.chatgpt.entity.fineTune.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class HyperParameters implements Serializable {

    @JsonProperty("batch_size")
    private String batchSize;
    @JsonProperty("learning_rate_multiplier")
    private String learningRateMultiplier;
    @JsonProperty("n_epochs")
    private String nEpochs;

}

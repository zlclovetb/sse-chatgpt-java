package com.paradm.chatgpt.entity.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 描述：金额消耗列表
 *
 * @author https:www.unfbx.com
 * @since 2023-04-08
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LineItem {
    /**
     * 模型名称
     */
    private String name;
    /**
     * 消耗金额
     */
    private BigDecimal cost;
}

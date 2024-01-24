package com.paradm.chatgpt;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paradm.chatgpt.constant.OpenAIConst;
import com.paradm.chatgpt.entity.billing.BillingUsage;
import com.paradm.chatgpt.entity.billing.Subscription;
import com.paradm.chatgpt.entity.chat.BaseChatCompletion;
import com.paradm.chatgpt.entity.chat.ChatCompletion;
import com.paradm.chatgpt.entity.chat.Functions;
import com.paradm.chatgpt.entity.chat.Message;
import com.paradm.chatgpt.entity.completions.Completion;
import com.paradm.chatgpt.exception.BaseException;
import com.paradm.chatgpt.exception.CommonError;
import com.paradm.chatgpt.function.KeyRandomStrategy;
import com.paradm.chatgpt.function.KeyStrategyFunction;
import com.paradm.chatgpt.interceptor.DefaultOpenAiAuthInterceptor;
import com.paradm.chatgpt.interceptor.DynamicKeyOpenAiAuthInterceptor;
import com.paradm.chatgpt.interceptor.OpenAiAuthInterceptor;
import com.paradm.chatgpt.plugin.PluginAbstract;
import com.paradm.chatgpt.plugin.PluginParam;
import com.paradm.chatgpt.sse.ConsoleEventSourceListener;
import com.paradm.chatgpt.sse.DefaultPluginListener;
import com.paradm.chatgpt.sse.PluginListener;
import io.reactivex.Single;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;


/**
 * 描述： open ai 客户端
 *
 * @author https:www.unfbx.com
 * 2023-02-28
 */

@Slf4j
public class OpenAiStreamClient {
    @Getter
    @NotNull
    private List<String> apiKey;
    /**
     * 自定义api host使用builder的方式构造client
     */
    @Getter
    private String apiHost;
    /**
     * 自定义的okHttpClient
     * 如果不自定义 ，就是用sdk默认的OkHttpClient实例
     */
    @Getter
    private OkHttpClient okHttpClient;

    /**
     * api key的获取策略
     */
    @Getter
    private KeyStrategyFunction<List<String>, String> keyStrategy;

    @Getter
    private OpenAiApi openAiApi;

    /**
     * 自定义鉴权处理拦截器<br/>
     * 可以不设置，默认实现：DefaultOpenAiAuthInterceptor <br/>
     * 如需自定义实现参考：DealKeyWithOpenAiAuthInterceptor
     *
     * @see DynamicKeyOpenAiAuthInterceptor
     * @see DefaultOpenAiAuthInterceptor
     */
    @Getter
    private OpenAiAuthInterceptor authInterceptor;

    /**
     * 构造实例对象
     *
     * @param builder
     */
    private OpenAiStreamClient(Builder builder) {
        if (CollectionUtil.isEmpty(builder.apiKey)) {
            throw new BaseException(CommonError.API_KEYS_NOT_NUL);
        }
        apiKey = builder.apiKey;

        if (StrUtil.isBlank(builder.apiHost)) {
            builder.apiHost = OpenAIConst.OPENAI_HOST;
        }
        apiHost = builder.apiHost;

        if (Objects.isNull(builder.keyStrategy)) {
            builder.keyStrategy = new KeyRandomStrategy();
        }
        keyStrategy = builder.keyStrategy;

        if (Objects.isNull(builder.authInterceptor)) {
            builder.authInterceptor = new DefaultOpenAiAuthInterceptor();
        }
        authInterceptor = builder.authInterceptor;
        //设置apiKeys和key的获取策略
        authInterceptor.setApiKey(this.apiKey);
        authInterceptor.setKeyStrategy(this.keyStrategy);

        if (Objects.isNull(builder.okHttpClient)) {
            builder.okHttpClient = this.okHttpClient();
        } else {
            //自定义的okhttpClient  需要增加api keys
            builder.okHttpClient = builder.okHttpClient
                    .newBuilder()
                    .addInterceptor(authInterceptor)
                    .build();
        }
        okHttpClient = builder.okHttpClient;

        this.openAiApi = new Retrofit.Builder()
                .baseUrl(apiHost)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create())
                .build().create(OpenAiApi.class);
    }

    /**
     * 创建默认的OkHttpClient
     */
    private OkHttpClient okHttpClient() {
        if (Objects.isNull(this.authInterceptor)) {
            this.authInterceptor = new DefaultOpenAiAuthInterceptor();
        }
        this.authInterceptor.setApiKey(this.apiKey);
        this.authInterceptor.setKeyStrategy(this.keyStrategy);
        return new OkHttpClient
                .Builder()
                .addInterceptor(this.authInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS)
                .readTimeout(50, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 问答接口 stream 形式
     *
     * @param completion          open ai 参数
     * @param eventSourceListener sse监听器
     * @see ConsoleEventSourceListener
     */
    public void streamCompletions(Completion completion, EventSourceListener eventSourceListener) {
        if (Objects.isNull(eventSourceListener)) {
            log.error("参数异常：EventSourceListener不能为空，可以参考：com.unfbx.chatgpt.sse.ConsoleEventSourceListener");
            throw new BaseException(CommonError.PARAM_ERROR);
        }
        if (StrUtil.isBlank(completion.getPrompt())) {
            log.error("参数异常：Prompt不能为空");
            throw new BaseException(CommonError.PARAM_ERROR);
        }
        if (!completion.isStream()) {
            completion.setStream(true);
        }
        if(Objects.isNull(completion.getApiUrl())){
            completion.setApiUrl("v1/completions");
        }
        try {
            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            ObjectMapper mapper = new ObjectMapper();
            String requestBody = mapper.writeValueAsString(completion);
            Request request = new Request.Builder()
                    .url(this.apiHost + completion.getApiUrl())
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                    .build();
            //创建事件
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
        } catch (JsonProcessingException e) {
            log.error("请求参数解析异常：{}", e);
            e.printStackTrace();
        } catch (Exception e) {
            log.error("请求参数解析异常：{}", e);
            e.printStackTrace();
        }
    }

    /**
     * 问答接口-简易版
     *
     * @param question            请求参数
     * @param eventSourceListener sse监听器
     * @see ConsoleEventSourceListener
     */
    public void streamCompletions(String question, EventSourceListener eventSourceListener) {
        Completion q = Completion.builder()
                .prompt(question)
                .stream(true)
                .build();
        this.streamCompletions(q, eventSourceListener);
    }

    /**
     * 流式输出，最新版的GPT-3.5 chat completion 更加贴近官方网站的问答模型
     *
     * @param chatCompletion      问答参数
     * @param eventSourceListener sse监听器
     * @see ConsoleEventSourceListener
     */
    public <T extends BaseChatCompletion> void streamChatCompletion(T chatCompletion, EventSourceListener eventSourceListener) {
        if (Objects.isNull(eventSourceListener)) {
            log.error("参数异常：EventSourceListener不能为空，可以参考：com.unfbx.chatgpt.sse.ConsoleEventSourceListener");
            throw new BaseException(CommonError.PARAM_ERROR);
        }
        if (!chatCompletion.isStream()) {
            chatCompletion.setStream(true);
        }
        if(Objects.isNull(chatCompletion.getApiUrl())){
            chatCompletion.setApiUrl("v1/chat/completions");
        }
        try {
            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            ObjectMapper mapper = new ObjectMapper();
            String requestBody = mapper.writeValueAsString(chatCompletion);
            Request request = new Request.Builder()
                    .url(this.apiHost + chatCompletion.getApiUrl())
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                    .build();
            //创建事件
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
        } catch (JsonProcessingException e) {
            log.error("请求参数解析异常：{}", e);
            e.printStackTrace();
        } catch (Exception e) {
            log.error("请求参数解析异常：{}", e);
            e.printStackTrace();
        }
    }

    /**
     * 流式输出，最新版的GPT-3.5 chat completion 更加贴近官方网站的问答模型
     * 警告：（不支持图片输入）
     *
     * @param messages            问答列表
     * @param eventSourceListener sse监听器
     * @see ConsoleEventSourceListener
     */
    public void streamChatCompletion(List<Message> messages, EventSourceListener eventSourceListener) {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .messages(messages)
                .stream(true)
                .build();
        this.streamChatCompletion(chatCompletion, eventSourceListener);
    }


    /**
     * 插件问答简易版
     * 默认取messages最后一个元素构建插件对话
     * 默认模型：ChatCompletion.Model.GPT_3_5_TURBO_16K_0613
     *
     * @param chatCompletion            参数
     * @param eventSourceListener       sse监听器
     * @param pluginEventSourceListener 插件sse监听器，收集function call返回信息
     * @param plugin                    插件
     * @param <R>                       插件自定义函数的请求值
     * @param <T>                       插件自定义函数的返回值
     */
    public <R extends PluginParam, T> void streamChatCompletionWithPlugin(ChatCompletion chatCompletion, EventSourceListener eventSourceListener, PluginListener pluginEventSourceListener, PluginAbstract<R, T> plugin) {
        if (Objects.isNull(plugin)) {
            this.streamChatCompletion(chatCompletion, eventSourceListener);
            return;
        }
        if (CollectionUtil.isEmpty(chatCompletion.getMessages())) {
            throw new BaseException(CommonError.MESSAGE_NOT_NUL);
        }
        Functions functions = Functions.builder()
                .name(plugin.getFunction())
                .description(plugin.getDescription())
                .parameters(plugin.getParameters())
                .build();
        //没有值，设置默认值
        if (Objects.isNull(chatCompletion.getFunctionCall())) {
            chatCompletion.setFunctionCall("auto");
        }
        //tip: 覆盖自己设置的functions参数，使用plugin构造的functions
        chatCompletion.setFunctions(Collections.singletonList(functions));
        //调用OpenAi
        if (Objects.isNull(pluginEventSourceListener)) {
            pluginEventSourceListener = new DefaultPluginListener(this, eventSourceListener, plugin, chatCompletion);
        }
        this.streamChatCompletion(chatCompletion, pluginEventSourceListener);
    }


    /**
     * 插件问答简易版
     * 默认取messages最后一个元素构建插件对话
     * 默认模型：ChatCompletion.Model.GPT_3_5_TURBO_16K_0613
     *
     * @param chatCompletion      参数
     * @param eventSourceListener sse监听器
     * @param plugin              插件
     * @param <R>                 插件自定义函数的请求值
     * @param <T>                 插件自定义函数的返回值
     */
    public <R extends PluginParam, T> void streamChatCompletionWithPlugin(ChatCompletion chatCompletion, EventSourceListener eventSourceListener, PluginAbstract<R, T> plugin) {
        PluginListener pluginEventSourceListener = new DefaultPluginListener(this, eventSourceListener, plugin, chatCompletion);
        this.streamChatCompletionWithPlugin(chatCompletion, eventSourceListener, pluginEventSourceListener, plugin);
    }


    /**
     * 插件问答简易版
     * 默认取messages最后一个元素构建插件对话
     * 默认模型：ChatCompletion.Model.GPT_3_5_TURBO_16K_0613
     *
     * @param messages            问答参数
     * @param eventSourceListener sse监听器
     * @param plugin              插件
     * @param <R>                 插件自定义函数的请求值
     * @param <T>                 插件自定义函数的返回值
     */
    public <R extends PluginParam, T> void streamChatCompletionWithPlugin(List<Message> messages, EventSourceListener eventSourceListener, PluginAbstract<R, T> plugin) {
        this.streamChatCompletionWithPlugin(messages, ChatCompletion.Model.GPT_3_5_TURBO_16K_0613.getName(), eventSourceListener, plugin);
    }

    /**
     * 插件问答简易版
     * 默认取messages最后一个元素构建插件对话
     *
     * @param messages            问答参数
     * @param model               模型
     * @param eventSourceListener eventSourceListener
     * @param plugin              插件
     * @param <R>                 插件自定义函数的请求值
     * @param <T>                 插件自定义函数的返回值
     */
    public <R extends PluginParam, T> void streamChatCompletionWithPlugin(List<Message> messages, String model, EventSourceListener eventSourceListener, PluginAbstract<R, T> plugin) {
        ChatCompletion chatCompletion = ChatCompletion.builder().messages(messages).model(model).build();
        this.streamChatCompletionWithPlugin(chatCompletion, eventSourceListener, plugin);
    }


    /**
     * 账户信息查询：里面包含总金额等信息
     *
     * @return 个人账户信息
     */
    public Subscription subscription() {
        Single<Subscription> subscription = this.openAiApi.subscription();
        return subscription.blockingGet();
    }

    /**
     * 账户调用接口消耗金额信息查询
     * 最多查询100天
     *
     * @param starDate 开始时间
     * @param endDate  结束时间
     * @return 消耗金额信息
     */
    public BillingUsage billingUsage(@NotNull LocalDate starDate, @NotNull LocalDate endDate) {
        Single<BillingUsage> billingUsage = this.openAiApi.billingUsage(starDate, endDate);
        return billingUsage.blockingGet();
    }

    /**
     * 构造
     *
     * @return Builder
     */
    public static OpenAiStreamClient.Builder builder() {
        return new OpenAiStreamClient.Builder();
    }

    public static final class Builder {
        private @NotNull List<String> apiKey;
        /**
         * api请求地址，结尾处有斜杠
         *
         * @see OpenAIConst
         */
        private String apiHost;

        /**
         * 自定义OkhttpClient
         */
        private OkHttpClient okHttpClient;


        /**
         * api key的获取策略
         */
        private KeyStrategyFunction keyStrategy;

        /**
         * 自定义鉴权拦截器
         */
        private OpenAiAuthInterceptor authInterceptor;

        public Builder() {
        }

        public Builder apiKey(@NotNull List<String> val) {
            apiKey = val;
            return this;
        }

        /**
         * @param val api请求地址，结尾处有斜杠
         * @return Builder
         * @see OpenAIConst
         */
        public Builder apiHost(String val) {
            apiHost = val;
            return this;
        }

        public Builder keyStrategy(KeyStrategyFunction val) {
            keyStrategy = val;
            return this;
        }

        public Builder okHttpClient(OkHttpClient val) {
            okHttpClient = val;
            return this;
        }

        public Builder authInterceptor(OpenAiAuthInterceptor val) {
            authInterceptor = val;
            return this;
        }

        public OpenAiStreamClient build() {
            return new OpenAiStreamClient(this);
        }
    }
}

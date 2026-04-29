package com.bohao.globalshop.service;

import java.util.List;

public interface TranslationService {
    /**
     * 启动ASR会话：建立与阿里云语音识别的长连接
     * @param roomId 直播间ID
     * @param targetLang 目标翻译语言 (en/ja/ko/th)
     * @param callback 识别结果回调（中间结果和最终结果）
     */
    void startAsrSession(Long roomId, String targetLang, AsrResultCallback callback);

    /**
     * 发送音频数据到ASR
     * @param roomId 直播间ID
     * @param audioData PCM音频数据
     */
    void sendAudioData(Long roomId, byte[] audioData);

    /**
     * 停止ASR会话
     * @param roomId 直播间ID
     */
    void stopAsrSession(Long roomId);

    /**
     * 将文本翻译为目标语言（调用通义千问）
     * @param text 源文本（中文）
     * @param targetLang 目标语言代码 (en/ja/ko/th)
     * @return 翻译后的文本
     */
    String translate(String text, String targetLang);

    /**
     * 获取支持的翻译目标语言列表
     */
    List<String> getSupportedLanguages();

    /**
     * 将文本合成为语音（调用阿里云TTS）
     * @param text 要合成的文本
     * @param lang 语言代码 (en/ja/ko/th/zh)
     * @return 合成的音频数据(PCM格式)，如果失败返回null
     */
    byte[] synthesizeSpeech(String text, String lang);

    /**
     * ASR识别结果回调接口
     */
    interface AsrResultCallback {
        /**
         * @param text 识别文字
         * @param isFinal 是否为最终结果（false表示中间结果）
         */
        void onResult(String text, boolean isFinal);

        void onError(String error);
    }
}

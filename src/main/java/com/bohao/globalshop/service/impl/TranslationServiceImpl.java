package com.bohao.globalshop.service.impl;

import com.bohao.globalshop.service.TranslationService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationServiceImpl implements TranslationService {

    private final ChatModel chatModel;

    @Value("${app.live.translation.nls-app-key}")
    private String nlsAppKey;

    @Value("${app.live.translation.nls-access-key}")
    private String nlsAccessKey;

    @Value("${app.live.translation.nls-access-secret}")
    private String nlsAccessSecret;

    @Value("${app.live.translation.max-concurrent-sessions}")
    private int maxConcurrentSessions;

    @Value("${app.live.translation.supported-languages}")
    private String supportedLanguages;


    // 管理每个直播间的ASR会话
    private final ConcurrentHashMap<Long, AsrSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 内部类：封装一个ASR会话的状态
     */
    private static class AsrSession {
        final Long roomId;
        final String targetLang;
        final AsrResultCallback callback;
        volatile boolean active = true;
        // 真实场景: SpeechTranscriber transcriber;

        AsrSession(Long roomId, String targetLang, AsrResultCallback callback) {
            this.roomId = roomId;
            this.targetLang = targetLang;
            this.callback = callback;
        }
    }

    @Override
    public void startAsrSession(Long roomId, String targetLang, AsrResultCallback callback) {
        if (activeSessions.size() >= maxConcurrentSessions) {
            callback.onError("同传翻译会话数已达上限: " + maxConcurrentSessions);
            return;
        }

        if (activeSessions.containsKey(roomId)) {
            log.warn("直播间 {} 已有ASR会话，先关闭旧会话", roomId);
            stopAsrSession(roomId);
        }

        AsrSession session = new AsrSession(roomId, targetLang, callback);
        activeSessions.put(roomId, session);
        log.info("ASR会话已启动 - 直播间: {}, 目标语言: {}", roomId, targetLang);

        /*
         * ========== 真实阿里云ASR集成代码（待SDK可用后启用）==========
         *
         * SpeechTranscriberListener listener = new SpeechTranscriberListener() {
         *     @Override
         *     public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
         *         // 中间结果
         *         callback.onResult(response.getTransSentenceText(), false);
         *     }
         *
         *     @Override
         *     public void onSentenceEnd(SpeechTranscriberResponse response) {
         *         // 最终结果（一句话完成）
         *         callback.onResult(response.getTransSentenceText(), true);
         *     }
         *
         *     @Override
         *     public void onFail(SpeechTranscriberResponse response) {
         *         callback.onError(response.getStatusText());
         *     }
         * };
         *
         * NlsClient nlsClient = new NlsClient(nlsAccessKey, nlsAccessSecret);
         * SpeechTranscriber transcriber = new SpeechTranscriber(nlsClient, listener);
         * transcriber.setAppKey(nlsAppKey);
         * transcriber.setFormat(InputFormatEnum.PCM);
         * transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
         * transcriber.setEnableIntermediateResult(true);
         * transcriber.start();
         * session.transcriber = transcriber;
         * ==========================================================
         */
    }

    @Override
    public void sendAudioData(Long roomId, byte[] audioData) {
        AsrSession session = activeSessions.get(roomId);
        if (session == null || !session.active) {
            return;
        }

        /*
         * 真实场景: session.transcriber.send(audioData);
         *
         * 模拟逻辑：每收到一定量的音频数据，模拟返回一个识别结果
         */
        log.debug("收到音频数据 - 直播间: {}, 大小: {} bytes", roomId, audioData.length);

        // 模拟：实际场景中ASR会通过listener回调返回结果
        // 这里不做模拟回调，等真实SDK接入后自然工作
    }

    @Override
    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String langName = getLanguageName(targetLang);

        String prompt = String.format(
                "你是专业的电商直播同声传译员。请将以下中文翻译为%s。" +
                        "要求：1.保持口语化自然流畅 2.专业术语准确 3.只输出译文，不要任何解释或前缀。\n\n%s",
                langName, text
        );

        try {
            String result = chatModel.chat(prompt);
            return result.trim();
        } catch (Exception e) {
            log.error("翻译失败 - 目标语言: {}, 文本: {}", targetLang, text, e);
            return text; // 翻译失败时返回原文
        }
    }

    @Override
    public List<String> getSupportedLanguages() {
        return Arrays.asList(supportedLanguages.split(","));
    }

    private String getLanguageName(String langCode) {
        return switch (langCode) {
            case "en" -> "English";
            case "ja" -> "Japanese(日本語)";
            case "ko" -> "Korean(한국어)";
            case "th" -> "Thai(ภาษาไทย)";
            default -> "English";
        };
    }

    @Override
    public byte[] synthesizeSpeech(String text, String lang) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        log.info("TTS合成请求 - 语言: {}, 文本: {}", lang, text);

        /*
         * ========== 真实阿里云TTS代码（待SDK可用后启用）==========
         *
         * String voice = getTtsVoice(lang); // 根据语言选择音色
         *
         * NlsClient nlsClient = new NlsClient(nlsAccessKey, nlsAccessSecret);
         * SpeechSynthesizer synthesizer = new SpeechSynthesizer(nlsClient, new SpeechSynthesizerListener() {
         *     private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
         *
         *     @Override
         *     public void onMessage(ByteBuffer message) {
         *         byte[] data = new byte[message.remaining()];
         *         message.get(data);
         *         audioBuffer.write(data, 0, data.length);
         *     }
         *
         *     @Override
         *     public void onComplete(SpeechSynthesizerResponse response) {
         *         // 合成完成
         *     }
         *
         *     @Override
         *     public void onFail(SpeechSynthesizerResponse response) {
         *         log.error("TTS合成失败: {}", response.getStatusText());
         *     }
         * });
         *
         * synthesizer.setAppKey(nlsAppKey);
         * synthesizer.setFormat(OutputFormatEnum.PCM);
         * synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
         * synthesizer.setVoice(voice);
         * synthesizer.setText(text);
         * synthesizer.start();
         * synthesizer.waitForComplete();
         *
         * return audioBuffer.toByteArray();
         * ===========================================================
         */

        // 模拟返回：实际部署时替换为真实SDK调用
        // 返回null表示TTS暂不可用，调用方会跳过语音推送
        return null;
    }

    /**
     * 根据语言代码获取阿里云TTS音色
     */
    private String getTtsVoice(String lang) {
        return switch (lang) {
            case "en" -> "eva_multilingual";    // 英语女声
            case "ja" -> "tomoka";              // 日语女声
            case "ko" -> "kyong";              // 韩语女声
            case "th" -> "tala";               // 泰语女声
            case "zh" -> "zhixiaobai";         // 中文女声
            default -> "eva_multilingual";
        };
    }

    @Override
    public void stopAsrSession(Long roomId) {
        AsrSession session = activeSessions.remove(roomId);
        if (session != null) {
            session.active = false;
            log.info("ASR会话已停止 - 直播间: {}", roomId);

            /*
             * 真实场景:
             * session.transcriber.stop();
             * session.transcriber.close();
             */
        }
    }
}

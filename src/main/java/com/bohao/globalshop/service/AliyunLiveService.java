package com.bohao.globalshop.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.time.Instant;

@Service
public class AliyunLiveService {

    @Value("${app.live.aliyun.push-domain}")
    private String pushDomain;

    @Value("${app.live.aliyun.pull-domain}")
    private String pullDomain;

    @Value("${app.live.aliyun.app-name}")
    private String appName;

    @Value("${app.live.aliyun.auth-key}")
    private String authKey;

    /**
     * 生成推流地址（带鉴权）
     * 格式: rtmp://{pushDomain}/{appName}/{streamKey}?auth_key={timestamp}-0-0-{md5hash}
     * MD5计算规则: md5("/{appName}/{streamKey}-{timestamp}-0-0-{authKey}")
     * 鉴权有效期默认24小时
     */
    public String generatePushUrl(String streamKey) {
        long timestamp = Instant.now().getEpochSecond() + 86400; // 24小时有效
        String uri = "/" + appName + "/" + streamKey;
        String authString = uri + "-" + timestamp + "-0-0-" + authKey;
        String md5Hash = md5(authString);
        return "rtmp://" + pushDomain + uri + "?auth_key=" + timestamp + "-0-0-" + md5Hash;
    }

    /**
     * 生成拉流地址（HTTP-FLV格式，低延迟）
     * 格式: https://{pullDomain}/{appName}/{streamKey}.flv?auth_key={timestamp}-0-0-{md5hash}
     */
    public String generatePullUrl(String streamKey) {
        long timestamp = Instant.now().getEpochSecond() + 86400;
        String uri = "/" + appName + "/" + streamKey + ".flv";
        String authString = uri + "-" + timestamp + "-0-0-" + authKey;
        String md5Hash = md5(authString);
        return "https://" + pullDomain + uri + "?auth_key=" + timestamp + "-0-0-" + md5Hash;
    }

    /**
     * 生成HLS拉流地址（兼容性更好）
     */
    public String generateHlsPullUrl(String streamKey) {
        long timestamp = Instant.now().getEpochSecond() + 86400;
        String uri = "/" + appName + "/" + streamKey + ".m3u8";
        String authString = uri + "-" + timestamp + "-0-0-" + authKey;
        String md5Hash = md5(authString);
        return "https://" + pullDomain + uri + "?auth_key=" + timestamp + "-0-0-" + md5Hash;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }
}

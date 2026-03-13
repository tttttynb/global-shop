package com.bohao.globalshop.common;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import java.util.Date;

public class JwtUtils {
    private static final String SECRET_KEY = "ByteDance_Offer_Must_Win_2026";
    // 通行证的有效期：设为 7 天 (单位：毫秒)
    private static final long EXPIRATION = 1000 * 60 * 60 * 24 * 7;

    /**
     * 根据用户的 id 和 username 生成通行证
     */
    public static String generateToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)// 在通行证里写上用户ID
                .withClaim("username", username)// 在通行证里写上用户名
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION))// 盖上过期时间戳
                .sign(Algorithm.HMAC256(SECRET_KEY));// 用我们的秘钥进行防伪签名
    }

    /**
     * 验证通行证的真伪，并从中解析出 userId
     */
    public static Long verifyToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(SECRET_KEY))
                    .build()
                    .verify(token)
                    .getClaim("userId").asLong();// 如果是真的，就把里面的 userId 提取出来
        } catch (Exception e) {
            // 如果签名不对、或者过期了，就会抛出异常，我们返回 null 代表验证失败
            return null;
        }

    }
}

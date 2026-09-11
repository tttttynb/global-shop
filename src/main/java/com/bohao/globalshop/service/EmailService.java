package com.bohao.globalshop.service;

/**
 * 邮件发送服务
 * <p>
 * 支持 mock 模式（默认）和真实 SMTP 模式。
 * 在 application.yml 中设置 app.notification.email.enabled=true 并配置 SMTP 即可启用真实发送。
 */
public interface EmailService {

    /**
     * 发送简单文本邮件
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件正文
     */
    void sendSimpleEmail(String to, String subject, String content);

    /**
     * 发送订单状态变更通知邮件
     *
     * @param to          收件人邮箱
     * @param orderId     订单ID
     * @param statusLabel 状态描述（"已支付"/"已发货"/"已签收"）
     * @param detailUrl   订单详情链接
     */
    void sendOrderStatusEmail(String to, Long orderId, String statusLabel, String detailUrl);
}

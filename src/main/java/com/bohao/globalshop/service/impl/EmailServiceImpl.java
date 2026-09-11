package com.bohao.globalshop.service.impl;

import com.bohao.globalshop.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务实现
 * <p>
 * 当 app.notification.email.enabled=false（默认）时，使用 Mock 模式——仅打印日志。
 * 设为 true 并配置 spring.mail.* 后启用真实 SMTP 发送。
 * <p>
 * 真实配置示例（application.yml）：
 * <pre>
 * spring:
 *   mail:
 *     host: smtp.qq.com
 *     port: 587
 *     username: your@qq.com
 *     password: your-auth-code
 *     properties:
 *       mail.smtp.auth: true
 *       mail.smtp.starttls.enable: true
 * </pre>
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String from;

    public EmailServiceImpl(@Value("${app.notification.email.enabled:false}") boolean emailEnabled,
                            @Value("${app.notification.email.from:noreply@globalshop.com}") String from,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender) {
        this.emailEnabled = emailEnabled;
        this.from = from;
        this.mailSender = mailSender;
        if (emailEnabled && mailSender == null) {
            log.warn("邮件功能已启用但未配置 spring.mail，将降级为 Mock 模式");
        } else if (!emailEnabled) {
            log.info("📧 邮件服务运行在 Mock 模式（仅打印日志），设置 app.notification.email.enabled=true 启用真实发送");
        }
    }

    @Override
    @Async
    public void sendSimpleEmail(String to, String subject, String content) {
        if (!canSend(to)) {
            mockSend(to, subject, content);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, false);
            mailSender.send(message);
            log.info("邮件已发送: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}", to, subject, e);
        }
    }

    @Override
    @Async
    public void sendOrderStatusEmail(String to, Long orderId, String statusLabel, String detailUrl) {
        String subject = "【GlobalShop】订单 #" + orderId + " " + statusLabel;
        String content = """
                亲爱的 GlobalShop 用户：

                您的订单 #%d 状态已更新为：%s

                点击查看订单详情：%s

                感谢您在 GlobalShop 购物！
                如有疑问，请联系客服。

                —— GlobalShop 团队
                """.formatted(orderId, statusLabel, detailUrl);
        sendSimpleEmail(to, subject, content);
    }

    private boolean canSend(String to) {
        return emailEnabled && mailSender != null && to != null && !to.isBlank();
    }

    private void mockSend(String to, String subject, String content) {
        log.info("""

                ========== 📧 [Mock邮件] ==========
                收件人: {}
                主题:   {}
                正文:
                {}
                =====================================
                """, to != null ? to : "(无)", subject, content);
    }
}

package com.intellidocAI.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending emails, primarily used for OTP verification during
 * signup.
 */
@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends an OTP verification email asynchronously.
     *
     * @param to  the recipient email address
     * @param otp the 6-digit OTP code
     */
    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Intelli-Doc AI — Verify Your Email");
            helper.setText(buildOtpEmailHtml(otp), true);

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.", e);
        }
    }

    /**
     * Builds a styled HTML email body for the OTP verification email.
     */
    private String buildOtpEmailHtml(String otp) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#0a0a0f;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0a0a0f;padding:40px 20px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background:linear-gradient(135deg,#1a1a2e 0%%,#16213e 100%%);border-radius:16px;border:1px solid rgba(0,212,255,0.2);overflow:hidden;">
                          <!-- Header -->
                          <tr>
                            <td style="padding:32px 40px 20px;text-align:center;">
                              <h1 style="margin:0;font-size:28px;font-weight:700;">
                                <span style="background:linear-gradient(135deg,#00d4ff,#7b2ff7);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;">Intelli-Doc AI</span>
                              </h1>
                              <p style="color:#8892b0;margin:8px 0 0;font-size:14px;">AI-Powered Code Documentation</p>
                            </td>
                          </tr>
                          <!-- Body -->
                          <tr>
                            <td style="padding:20px 40px 32px;">
                              <h2 style="color:#e6f1ff;margin:0 0 12px;font-size:20px;font-weight:600;">Verify Your Email</h2>
                              <p style="color:#8892b0;margin:0 0 24px;font-size:15px;line-height:1.6;">
                                Use the verification code below to complete your account setup. This code expires in <strong style="color:#00d4ff;">10 minutes</strong>.
                              </p>
                              <!-- OTP Code -->
                              <div style="background:rgba(0,212,255,0.08);border:1px solid rgba(0,212,255,0.25);border-radius:12px;padding:24px;text-align:center;margin-bottom:24px;">
                                <span style="font-size:36px;font-weight:700;letter-spacing:12px;color:#00d4ff;font-family:'Courier New',monospace;">%s</span>
                              </div>
                              <p style="color:#6b7280;margin:0;font-size:13px;line-height:1.5;">
                                If you didn't create an account with Intelli-Doc AI, you can safely ignore this email.
                              </p>
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="padding:20px 40px;border-top:1px solid rgba(255,255,255,0.06);text-align:center;">
                              <p style="color:#4a5568;margin:0;font-size:12px;">&copy; 2026 Intelli-Doc AI. All rights reserved.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .formatted(otp);
    }
}

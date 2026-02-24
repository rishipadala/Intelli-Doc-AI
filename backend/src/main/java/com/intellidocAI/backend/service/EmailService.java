package com.intellidocAI.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Service for sending emails via Brevo (formerly Sendinblue) HTTP API.
 * Uses HTTPS instead of SMTP to avoid port-blocking on hosting platforms like
 * HuggingFace.
 */
@Slf4j
@Service
public class EmailService {

  private final WebClient webClient;

  @Value("${app.brevo.apiKey:}")
  private String brevoApiKey;

  @Value("${app.brevo.senderEmail:intellidocai.helpdesk@gmail.com}")
  private String senderEmail;

  @Value("${app.brevo.senderName:Intelli-Doc AI}")
  private String senderName;

  public EmailService(WebClient.Builder webClientBuilder) {
    this.webClient = webClientBuilder
        .baseUrl("https://api.brevo.com/v3")
        .build();
  }

  /**
   * Sends an OTP verification email asynchronously via Brevo HTTP API.
   *
   * @param to  the recipient email address
   * @param otp the 6-digit OTP code
   */
  @Async
  public void sendOtpEmail(String to, String otp) {
    try {
      Map<String, Object> emailPayload = Map.of(
          "sender", Map.of("name", senderName, "email", senderEmail),
          "to", List.of(Map.of("email", to)),
          "subject", "Intelli-Doc AI — Verify Your Email",
          "htmlContent", buildOtpEmailHtml(otp));

      String response = webClient.post()
          .uri("/smtp/email")
          .header("api-key", brevoApiKey)
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .bodyValue(emailPayload)
          .retrieve()
          .bodyToMono(String.class)
          .block();

      log.info("OTP email sent successfully to: {} | Response: {}", to, response);
    } catch (Exception e) {
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

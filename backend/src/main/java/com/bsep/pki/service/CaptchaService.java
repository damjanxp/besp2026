package com.bsep.pki.service;

import com.bsep.pki.exception.CaptchaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CaptchaService {

    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.verify-url}")
    private String verifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public String verifyRecaptcha(String captchaToken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", captchaToken);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(verifyUrl, params, Map.class);
        } catch (Exception e) {
            log.error("Failed to contact reCAPTCHA service", e);
            throw new CaptchaException("Captcha verifikacija neuspjesna");
        }

        if (response == null) {
            throw new CaptchaException("Captcha verifikacija neuspjesna");
        }

        Boolean success = (Boolean) response.get("success");
        if (Boolean.TRUE.equals(success)) {
            return null;
        }

        List<String> errorCodes = (List<String>) response.get("error-codes");
        String errorMessage = errorCodes != null && !errorCodes.isEmpty()
                ? String.join(", ", errorCodes)
                : "Captcha verifikacija neuspjesna";

        throw new CaptchaException(errorMessage);
    }
}


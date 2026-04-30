package com.bsep.pki.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 12;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        boolean hasMinLength = password.length() >= MIN_LENGTH;
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));

        if (hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecial) {
            return true;
        }

        // Build detailed message
        context.disableDefaultConstraintViolation();
        StringBuilder msg = new StringBuilder("Password must: ");
        if (!hasMinLength) msg.append("be at least 12 characters; ");
        if (!hasUppercase) msg.append("contain at least one uppercase letter; ");
        if (!hasLowercase) msg.append("contain at least one lowercase letter; ");
        if (!hasDigit) msg.append("contain at least one digit; ");
        if (!hasSpecial) msg.append("contain at least one special character; ");

        context.buildConstraintViolationWithTemplate(msg.toString().trim())
                .addConstraintViolation();

        return false;
    }
}


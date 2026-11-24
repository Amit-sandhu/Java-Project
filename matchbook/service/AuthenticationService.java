package matchbook.service;

import matchbook.db.DatabaseManager;
import matchbook.exceptions.AuthenticationException;
import matchbook.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.spec.PBEKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;

public class AuthenticationService {
    private final DatabaseManager database;

    public AuthenticationService(DatabaseManager database) {
        this.database = database;
    }

    public void register(String username, String email, String password,
                         String recoveryAnswer) throws Exception {
        if (username.length() < 3 || password.length() < 8) {
            throw new AuthenticationException(
                    "Username must have at least 3 characters and password at least 8.");
        }
        if (!email.contains("@") || !email.contains(".")) {
            throw new AuthenticationException("Enter a valid email address.");
        }
        if (recoveryAnswer.trim().length() < 2) {
            throw new AuthenticationException("Recovery answer is too short.");
        }

        byte[] passwordSalt = makeSalt();
        byte[] recoverySalt = makeSalt();

        database.createUser(username, email,
                hash(password.toCharArray(), passwordSalt),
                Base64.getEncoder().encodeToString(passwordSalt),
                hash(recoveryAnswer.trim().toLowerCase().toCharArray(), recoverySalt),
                Base64.getEncoder().encodeToString(recoverySalt));
    }

    public User login(String username, String password) throws Exception {
        String[] credentials = getCredentialHashes(username);
        if (credentials == null) {
            throw new AuthenticationException("Invalid username or password.");
        }

        byte[] salt = Base64.getDecoder().decode(credentials[1]);
        String passwordHash = hash(password.toCharArray(), salt);
        User user = database.authenticate(username, passwordHash);

        if (user == null) {
            throw new AuthenticationException("Invalid username or password.");
        }
        return user;
    }

    private String[] getCredentialHashes(String username) throws SQLException {
        String sql = "SELECT password_salt FROM users WHERE username=?";
        try (var statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    return new String[] {"", result.getString("password_salt")};
                }
            }
        }
        return null;
    }

    public void resetPassword(String username, String email, String recoveryAnswer,
                              String newPassword) throws Exception {
        if (newPassword.length() < 8) {
            throw new AuthenticationException("Password must have at least 8 characters.");
        }

        String[] values = getRecoveryDetails(username);
        if (values == null || !values[0].equalsIgnoreCase(email)) {
            throw new AuthenticationException("Recovery details did not match.");
        }

        byte[] recoverySalt = Base64.getDecoder().decode(values[2]);
        String recoveryHash = hash(
                recoveryAnswer.trim().toLowerCase().toCharArray(), recoverySalt);

        byte[] newSalt = makeSalt();
        String newHash = hash(newPassword.toCharArray(), newSalt);

        boolean updated = database.resetPassword(username, email, recoveryHash,
                newHash, Base64.getEncoder().encodeToString(newSalt));

        if (!updated) {
            throw new AuthenticationException("Recovery details did not match.");
        }
    }

    private String[] getRecoveryDetails(String username) throws SQLException {
        String sql = "SELECT email,recovery_hash,recovery_salt FROM users WHERE username=?";
        try (var statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    return new String[] {
                            result.getString("email"),
                            result.getString("recovery_hash"),
                            result.getString("recovery_salt")
                    };
                }
            }
        }
        return null;
    }

    private byte[] makeSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private String hash(char[] value, byte[] salt) throws Exception {
        PBEKeySpec specification = new PBEKeySpec(value, salt, 120000, 256);
        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] result = factory.generateSecret(specification).getEncoded();
        specification.clearPassword();
        return Base64.getEncoder().encodeToString(result);
    }
}

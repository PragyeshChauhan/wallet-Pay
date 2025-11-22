package com.ezpay.infraservice.banner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Collectors;

public class EzPayBanner implements Banner {
    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        String logo = loadBannerFromTxt();

        String appName = getEnv(environment, "spring.application.name", "Unknown");
        String profile = getEnv(environment, "spring.profiles.active", "default");
        String port = getEnv(environment, "server.port", "8080");
        String javaVersion = System.getProperty("java.version", "Unknown");
        String springBootVersion =SpringBootVersion.getVersion();
        String startedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String details = String.format("""
                :: << EZ PAY Wallet >> ::
                -----------------------------------------
                :: Service       ::  %s
                :: Profile       ::  %s
                :: Port          ::  %s
                :: Java Version  ::  %s
                :: Spring Boot   ::  %s
                :: Started At    ::  %s
                :: Author        ::  Pragyesh Chauhan
                -----------------------------------------
                """, appName, profile, port, javaVersion, springBootVersion, startedAt);

        out.println(logo);
        out.println(details);
    }

    private String getEnv(Environment env, String key, String defaultVal) {
        if (env == null) {
            return defaultVal;
        }
        try {
            String val = env.getProperty(key);
            return val != null ? val : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String loadBannerFromTxt() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("custombanner.txt")),
                        StandardCharsets.UTF_8
                ))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return ":: Unable to load banner.txt ::";
        }
    }
}
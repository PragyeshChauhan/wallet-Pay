package com.ezpay.frauddetectionservice.services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnomalyDetectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private static final int WINDOW_SIZE = 250;
    private static final double INITIAL_THRESHOLD = 2.7;
    private static final long INACTIVE_TIMEOUT_MINUTES = 60;

    private final ConcurrentHashMap<String, CircularBuffer> deviceBuffers = new ConcurrentHashMap<>();
    private double adaptiveThreshold = INITIAL_THRESHOLD;

    public boolean detectAnomaly(long issuedAtMillis, String deviceId) {
        CircularBuffer buffer = deviceBuffers.computeIfAbsent(deviceId, k -> new CircularBuffer(WINDOW_SIZE));
        synchronized (buffer) {
            buffer.add(issuedAtMillis);
        }

        double zScore = calculateZScore(buffer);
        adaptThreshold(zScore);
        boolean isAnomalous = zScore > adaptiveThreshold;

        if (isAnomalous) {
            LOGGER.warn("Anomaly detected for device {} at {} IST (Z-score: {}, threshold: {})",
                    deviceId, Instant.now(), zScore, adaptiveThreshold);
        } else {
            LOGGER.debug("No anomaly for device {} at {} IST (Z-score: {}, threshold: {})",
                    deviceId, Instant.now(), zScore, adaptiveThreshold);
        }
        return isAnomalous;
    }

    private double calculateZScore(CircularBuffer buffer) {
        double[] values = buffer.getValues();
        if (values.length < 2 || allInvalid(values)) return 0.0;

        double mean = calculateMean(values);
        double stdDev = calculateStandardDeviation(values, mean);
        double latestValue = values[values.length - 1];

        return stdDev > 0 ? Math.abs((latestValue - mean) / stdDev) : 0.0;
    }

    private void adaptThreshold(double zScore) {
        double adjustmentFactor = zScore > adaptiveThreshold ? 0.1 : -0.1;
        adaptiveThreshold = Math.max(1.5, Math.min(3.8, adaptiveThreshold + adjustmentFactor * (zScore / INITIAL_THRESHOLD)));
    }

    private double calculateMean(double[] values) {
        double sum = 0.0;
        int validCount = 0;
        for (double value : values) {
            if (value > 0) {
                sum += value;
                validCount++;
            }
        }
        return validCount > 0 ? sum / validCount : 0.0;
    }

    private double calculateStandardDeviation(double[] values, double mean) {
        double sumSquaredDiff = 0.0;
        int validCount = 0;
        for (double value : values) {
            if (value > 0) {
                sumSquaredDiff += Math.pow(value - mean, 2);
                validCount++;
            }
        }
        return validCount > 1 ? Math.sqrt(sumSquaredDiff / (validCount - 1)) : 0.0;
    }

    private boolean allInvalid(double[] values) {
        for (double value : values) {
            if (value > 0) return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanupInactiveDevices() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(INACTIVE_TIMEOUT_MINUTES));
        for (Map.Entry<String, CircularBuffer> entry : deviceBuffers.entrySet()) {
            CircularBuffer buffer = entry.getValue();
            synchronized (buffer) {
                double[] values = buffer.getValues();
                if (values.length == 0 || allInvalid(values)) {
                    deviceBuffers.remove(entry.getKey());
                } else if (values[values.length - 1] < cutoff.toEpochMilli()) {
                    deviceBuffers.remove(entry.getKey());
                }
            }
        }
        LOGGER.info("Cleaned up inactive device buffers at {} IST", Instant.now());
    }

    private static class CircularBuffer {
        private final double[] buffer;
        private int index = 0;
        private int size = 0;

        CircularBuffer(int capacity) {
            this.buffer = new double[capacity];
            for (int i = 0; i < capacity; i++) buffer[i] = -1;
        }

        synchronized void add(double value) {
            buffer[index] = value;
            index = (index + 1) % buffer.length;
            size = Math.min(size + 1, buffer.length);
        }

        double[] getValues() {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = buffer[(index - size + i + buffer.length) % buffer.length];
            }
            return result;
        }
    }
}


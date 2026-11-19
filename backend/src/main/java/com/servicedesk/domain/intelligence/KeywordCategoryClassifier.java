package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rule-based classifier: scores each category by the domain keywords found in the ticket text.
 * Title words count double because reporters tend to put the core symptom there.
 *
 * <p>Deterministic and explainable (it returns the words it matched), which matters more for a
 * help desk than a few points of accuracy. A trained model could replace it behind the same
 * {@link CategorySuggestionStrategy} interface.
 */
public class KeywordCategoryClassifier implements CategorySuggestionStrategy {

  private static final double MIN_CONFIDENCE = 0.4;

  private static final Map<Category, Set<String>> KEYWORDS = new EnumMap<>(Category.class);

  static {
    KEYWORDS.put(Category.NETWORK, Set.of(
        "network", "wifi", "wi-fi", "wireless", "internet", "ethernet", "lan", "vpn", "dns",
        "dhcp", "router", "switch", "gateway", "ping", "latency", "packet", "connectivity",
        "offline", "firewall", "bandwidth", "ip"));
    KEYWORDS.put(Category.HARDWARE, Set.of(
        "laptop", "desktop", "monitor", "keyboard", "mouse", "printer", "projector", "screen",
        "battery", "charger", "hardware", "disk", "ram", "fan", "overheating", "scanner",
        "headset", "webcam", "power", "device", "bulb", "cable"));
    KEYWORDS.put(Category.SOFTWARE, Set.of(
        "software", "install", "installation", "update", "upgrade", "license", "crash",
        "crashes", "freezes", "bug", "excel", "word", "office", "windows", "macos", "driver",
        "antivirus", "patch", "version"));
    KEYWORDS.put(Category.ACCESS, Set.of(
        "password", "login", "log-in", "signin", "sign-in", "locked", "lockout", "account",
        "access", "permission", "permissions", "mfa", "2fa", "otp", "sso", "authentication",
        "unauthorized", "credentials", "reset"));
    KEYWORDS.put(Category.DATABASE, Set.of(
        "database", "db", "sql", "query", "queries", "postgres", "mysql", "oracle", "table",
        "deadlock", "replication", "backup", "restore", "schema", "records"));
    KEYWORDS.put(Category.CLOUD, Set.of(
        "cloud", "aws", "azure", "gcp", "kubernetes", "container", "docker", "vm", "instance",
        "serverless", "saas", "tenant", "region"));
    KEYWORDS.put(Category.SECURITY, Set.of(
        "security", "phishing", "malware", "virus", "ransomware", "breach", "suspicious",
        "spam", "hacked", "compromised", "vulnerability", "leak", "attack", "scam"));
    KEYWORDS.put(Category.APPLICATION, Set.of(
        "application", "app", "portal", "website", "page", "payment", "transaction",
        "checkout", "erp", "lms", "moodle", "error", "500", "timeout", "slow", "unavailable",
        "submit", "form", "email", "outlook", "teams"));
    KEYWORDS.put(Category.STORAGE, Set.of(
        "storage", "drive", "onedrive", "sharepoint", "quota", "files", "file", "folder",
        "upload", "download", "share", "nas", "space", "sync"));
  }

  @Override
  public Optional<Suggestion> suggest(String title, String description) {
    List<String> titleWords = tokenize(title);
    List<String> bodyWords = tokenize(description);
    if (titleWords.isEmpty() && bodyWords.isEmpty()) {
      return Optional.empty();
    }

    Map<Category, Integer> scores = new EnumMap<>(Category.class);
    Map<Category, Set<String>> evidence = new EnumMap<>(Category.class);
    accumulate(titleWords, 2, scores, evidence);
    accumulate(bodyWords, 1, scores, evidence);

    int total = scores.values().stream().mapToInt(Integer::intValue).sum();
    if (total == 0) {
      return Optional.empty();
    }
    Map.Entry<Category, Integer> best =
        scores.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();

    // Share of all keyword weight that went to the winner, damped for very short evidence.
    double share = (double) best.getValue() / total;
    double confidence = Math.min(1.0, share * Math.min(1.0, best.getValue() / 3.0));
    if (confidence < MIN_CONFIDENCE) {
      return Optional.empty();
    }
    return Optional.of(
        new Suggestion(
            best.getKey(),
            Math.round(confidence * 100) / 100.0,
            new ArrayList<>(evidence.get(best.getKey()))));
  }

  private static void accumulate(
      List<String> words, int weight, Map<Category, Integer> scores, Map<Category, Set<String>> evidence) {
    for (String word : words) {
      for (Map.Entry<Category, Set<String>> entry : KEYWORDS.entrySet()) {
        if (entry.getValue().contains(word)) {
          scores.merge(entry.getKey(), weight, Integer::sum);
          evidence.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).add(word);
        }
      }
    }
  }

  private static List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<String> words = new ArrayList<>();
    for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+")) {
      String word = raw.replaceAll("^-+|-+$", "");
      if (!word.isEmpty()) {
        words.add(word);
      }
    }
    return words;
  }
}

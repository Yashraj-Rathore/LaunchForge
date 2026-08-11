package dev.launchforge.sdk;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict SemVer 2.0.0 precedence implementation used only after snapshot validation. */
final class SemanticVersion implements Comparable<SemanticVersion> {
  private static final Pattern SYNTAX =
      Pattern.compile(
          "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
              + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
              + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
              + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

  private final BigInteger major;
  private final BigInteger minor;
  private final BigInteger patch;
  private final List<Identifier> prerelease;

  private SemanticVersion(
      BigInteger major, BigInteger minor, BigInteger patch, List<Identifier> prerelease) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.prerelease = List.copyOf(prerelease);
  }

  static SemanticVersion parse(String value) {
    Matcher matcher = SYNTAX.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid semantic version");
    }
    List<Identifier> prerelease = new ArrayList<>();
    if (matcher.group(4) != null) {
      for (String identifier : matcher.group(4).split("\\.")) {
        prerelease.add(Identifier.parse(identifier));
      }
    }
    return new SemanticVersion(
        new BigInteger(matcher.group(1)),
        new BigInteger(matcher.group(2)),
        new BigInteger(matcher.group(3)),
        prerelease);
  }

  static SemanticVersion parseOrNull(Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    try {
      return parse(text);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int comparison = major.compareTo(other.major);
    if (comparison == 0) {
      comparison = minor.compareTo(other.minor);
    }
    if (comparison == 0) {
      comparison = patch.compareTo(other.patch);
    }
    if (comparison != 0) {
      return comparison;
    }
    if (prerelease.isEmpty()) {
      return other.prerelease.isEmpty() ? 0 : 1;
    }
    if (other.prerelease.isEmpty()) {
      return -1;
    }
    int sharedLength = Math.min(prerelease.size(), other.prerelease.size());
    for (int index = 0; index < sharedLength; index++) {
      comparison = prerelease.get(index).compareTo(other.prerelease.get(index));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(prerelease.size(), other.prerelease.size());
  }

  private record Identifier(String text, BigInteger numeric) implements Comparable<Identifier> {
    static Identifier parse(String text) {
      return text.chars().allMatch(Character::isDigit)
          ? new Identifier(text, new BigInteger(text))
          : new Identifier(text, null);
    }

    @Override
    public int compareTo(Identifier other) {
      if (numeric != null && other.numeric != null) {
        return numeric.compareTo(other.numeric);
      }
      if (numeric != null) {
        return -1;
      }
      if (other.numeric != null) {
        return 1;
      }
      return text.compareTo(other.text);
    }
  }
}

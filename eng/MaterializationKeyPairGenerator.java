import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Generates one local Ed25519 materialization key pair for the ignored demo environment. */
final class MaterializationKeyPairGenerator {
  private MaterializationKeyPairGenerator() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 0) {
      throw new IllegalArgumentException("This generator accepts no arguments");
    }
    KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    System.out.println(encoder.encodeToString(pair.getPrivate().getEncoded()));
    System.out.println(encoder.encodeToString(pair.getPublic().getEncoded()));
  }
}

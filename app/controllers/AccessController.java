package controllers;

import org.apache.commons.codec.binary.Base64;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.SecurityService;
import utils.StorageConfiguration;
import utils.security.AccessTokenManager;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

/**
 * Access token manager.
 */
public class AccessController extends Controller {
    @Inject
    private SecurityService security;

    public AccessController() {
    }

    /**
     * Request an access token to the administration features.
     * You must set the following headers:
     * - User-Public-Key: RSA public kad_key
     * - User-Key-Signature: Signature of SHA256(User-Public-Key) with Noixion's private kad_key
     *
     * @return The access token encrypted with RSA and your user public kad_key.
     */
    public Result getAccessToken(Http.Request request) {
        StorageConfiguration.load();
        // Obtain the public kad_key and the signature
        String pubicKeyBase64 = request.getHeaders().get("User-Public-Key").orElse("");
        String signatureBase64 =  request.getHeaders().get("User-Key-Signature").orElse("");
        if (pubicKeyBase64 == null || signatureBase64 == null) {
            return badRequest("Debe especificar una clave pública y una firma.");
        }
        PublicKey receivedKey;
        byte[] receivedSignature;
        try {
            receivedKey = security.buildPublicKey(pubicKeyBase64);
        } catch (Exception ex) {
            Logger.of(AccessController.class).debug("Clave pública o firma incorrectas.");
            return badRequest("Clave pública o firma incorrectas.");
        }
        receivedSignature = Base64.decodeBase64(signatureBase64);

        // Validate public kad_key and signature
        if (!security.validatePublicKey(receivedKey, receivedSignature)) {
            Logger.of(AccessController.class).debug("Clave pública o firma incorrectas.");
            return badRequest("Clave pública o firma incorrectas.");
        }

        // Generate a new access token
        String token = AccessTokenManager.getInstance().generateAccessToken();

        // Encrypt the token
        byte[] encryptedToken;
        try {
            encryptedToken = security.encryptString(token, receivedKey);
        } catch (InvalidKeyException | IllegalBlockSizeException ex) {
            ex.printStackTrace();
            return badRequest();
        } catch (NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException ex) {
            ex.printStackTrace();
            return internalServerError();
        }

        return ok(Base64.encodeBase64String(encryptedToken)).as("text/plain");
    }
}

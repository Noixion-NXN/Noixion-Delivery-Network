package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.DHTService;
import utils.StorageConfiguration;
import utils.StoragePaths;
import views.html.*;

import javax.inject.Inject;


public class Application extends Controller {

    @Inject
    public DHTService dht;

    public Result index() {
	    return ok(index.render());
    }

    public Result routingTable() {
        return ok(dht.getRoutingTable().toString());
    }

    public Result serverInformation() {
        ObjectNode ret = Json.newObject();

        ret.put("vendor", System.getProperty("java.vendor"));
        ret.put("version", System.getProperty("java.version"));
        ret.put("arch", System.getProperty("os.arch"));
        ret.set("os", Json.newObject()
                .put("name", System.getProperty("os.name"))
                .put("version", System.getProperty("os.version")));

        ret.set("storage", Json.newObject()
                .put("free", StoragePaths.getVideosStoragePath().toFile().getUsableSpace())
                .put("total", StoragePaths.getVideosStoragePath().toFile().getTotalSpace()));

        return ok(ret);
    }

    public Result checkRegistration(String key) {
        StorageConfiguration.load();
        if (key.equals(StorageConfiguration.REGISTRATION_KEY)) {
            return serverInformation();
        } else {
            return forbidden();
        }
    }
}
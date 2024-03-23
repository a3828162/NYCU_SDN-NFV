package nycu.sdnfv.vrouter;

import java.util.ArrayList;
import java.util.List;

//import java.util.List;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

import com.google.common.base.Function;

public class VRouterConfig extends Config<ApplicationId> {

    public static final String QUAGA = "quagga";
    public static final String QUAGA_MAC = "quagga-mac";
    public static final String VIRTUAL_IP = "virtual-ip";
    public static final String VIRTUAL_MAC = "virtual-mac";
    public static final String PEERS = "peers";
    Function<String, String> func = (String e) -> {
      return e; };

    public String quaga() {
      return get(QUAGA, null);
    }

    public String quagamac() {
      return get(QUAGA_MAC, null);
    }

    public String virtualip() {
      return get(VIRTUAL_IP, null);
    }

    public String virtualmac() {
      return get(VIRTUAL_MAC, null);
    }

    public List<String> peers() {
      List<String> peers = new ArrayList<String>();
      for (String peer : getList(PEERS, func)) {
        peers.add(peer);
      }
      return peers;
    }
}
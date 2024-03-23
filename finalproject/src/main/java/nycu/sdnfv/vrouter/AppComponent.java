/*
 * Copyright 2023-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.sdnfv.vrouter;


import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
//import org.onlab.packet.IPv4;
//import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
//import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;

import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
//import org.onosproject.net.Host;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;

import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
//import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.InboundPacket;
//import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteEvent;
import org.onosproject.routeservice.RouteInfo;
import org.onosproject.routeservice.RouteListener;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.RouteTableId;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
//import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

//import static org.onlab.util.Tools.defaultOffsetDataTime;
import static org.onlab.util.Tools.get;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
//import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
//import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry networkConfigRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    private VRouterPacketProcessor processor = new VRouterPacketProcessor();

    private final VRouterConfigListener cfgListener = new VRouterConfigListener();
    private final ConfigFactory<ApplicationId, VRouterConfig> factory =
    new ConfigFactory<ApplicationId, VRouterConfig>(
        APP_SUBJECT_FACTORY, VRouterConfig.class, "router") {
      @Override
      public VRouterConfig createConfig() {
        return new VRouterConfig();
      }
    };

    private final VRouterListener vRouterListener = new VRouterListener();

    private ApplicationId appId;
    private VRouterConfig vRouterConfig;

    //private List<String> quagaIP = Arrays.asList("172.30.1.1", "172.30.2.1", "172.30.3.1");
    private List<String> quagaIP;
    private List<String> peers;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());

        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        log.info("Started");
        networkConfigRegistry.registerConfigFactory(factory);
        vRouterConfig = networkConfigRegistry.getConfig(appId, VRouterConfig.class);
        networkConfigRegistry.addListener(cfgListener);

        packetService.addProcessor(processor, PacketProcessor.director(6));
        routeService.addListener(vRouterListener);
        quagaIP =  new ArrayList<>();
        log.info("appIDd = {}", appId);
        log.info("Config = {}", vRouterConfig);

        if (vRouterConfig != null) {
            setQuagaIp();
            setPeers();
            installBgpRule();
        }

        installTransiantRule();
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        networkConfigRegistry.unregisterConfigFactory(factory);
        networkConfigRegistry.removeListener(cfgListener);
        packetService.removeProcessor(processor);
        routeService.removeListener(vRouterListener);
        quagaIP = null;
        log.info("Stopped");
        vRouterConfig = null;

    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private class VRouterPacketProcessor implements PacketProcessor {

        /**
         * Process the packet
         *
         * @param context content of the incoming message
         */
        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null || ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                return;
            }

            if (isControlPacket(ethPkt)) {
                return;
            }

            if (ethPkt.getDestinationMAC().isLldp()) {
                return;
            }
            IPv4 ipPayload = (IPv4) ethPkt.getPayload();
            Ip4Address ip4Address = Ip4Address.valueOf(ipPayload.getDestinationAddress());
            Ip4Address srcIP4Address = Ip4Address.valueOf(ipPayload.getSourceAddress());
            log.info("Success get Packet!!!");
            log.info("Target IPAddress = {}", ip4Address);

            if (containInPrefix(IpPrefix.valueOf("192.168.50.0/24"), srcIP4Address)
                && containInPrefix(IpPrefix.valueOf("192.168.50.0/24"), ip4Address)) {
                log.info("For bridge process = {}, = {}", srcIP4Address, ip4Address);
                return;
            }

            if (containInPrefix(IpPrefix.valueOf("192.168.50.0/24"), ip4Address)
                || containOutPrefix(ip4Address)) {
                log.info("Contain in routers");
                context.block();
                installExternalRule(context);
                return;
            }

        }
    }

    private void setQuagaIp() {

        for (Interface intf : intfService.getInterfaces()) {
            quagaIP.add(intf.ipAddressesList().get(0).ipAddress().toString());
        }
        Collections.sort(quagaIP);
        log.info("IntfService Total = {}", quagaIP);
    }

    private void setPeers() {
        /*log.info("0000000000000");
        String[] con = vRouterConfig.toString().split(",");
        log.info("peers = {}, {}, {}", con[4].subSequence(10, 20),
            con[5].subSequence(1, 11), con[6].subSequence(1, 11));
        peers = Arrays.asList(con[4].subSequence(10, 20).toString(),
            con[5].subSequence(1, 11).toString(),
            con[6].subSequence(1, 11).toString());*/
        peers = vRouterConfig.peers();
        Collections.sort(peers);
        log.info("Peers = {}", peers);
    }

    private void installBgpRule() {
        for (int i = 0; i < quagaIP.size(); i++) {

            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf(peers.get(i) + "/32")).matchEthType(Ethernet.TYPE_IPV4);
            TrafficSelector.Builder selector2 = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf(quagaIP.get(i) + "/32")).matchEthType(Ethernet.TYPE_IPV4);
            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            ConnectPoint ingressPoint = ConnectPoint.fromString(vRouterConfig.quaga());
            FilteredConnectPoint filterIngressPoint = new FilteredConnectPoint(ingressPoint);
            log.info("ingress = {}", ingressPoint);
            log.info("ipv4 = {}", quagaIP.get(i));

            ConnectPoint egressPoint = intfService
            .getMatchingInterface(Ip4Address.valueOf(quagaIP.get(i))).connectPoint();
            FilteredConnectPoint filterEgressPoint = new FilteredConnectPoint(egressPoint);
            log.info("egress = {}", egressPoint);

            PointToPointIntent pointIntent = PointToPointIntent.builder()
                .appId(appId)
                .filteredIngressPoint(filterIngressPoint).filteredEgressPoint(filterEgressPoint)
                .selector(selector.build())
                .treatment(treatment)
                .build();

            PointToPointIntent pointIntentReverse = PointToPointIntent.builder()
                .appId(appId)
                .filteredIngressPoint(filterEgressPoint).filteredEgressPoint(filterIngressPoint)
                .selector(selector2.build())
                .treatment(treatment)
                .build();

            intentService.submit(pointIntent);
            intentService.submit(pointIntentReverse);
        }
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    private void installExternalRule(PacketContext context) {
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPayload = (IPv4) ethPkt.getPayload();
        Ip4Address srcIP4Address = Ip4Address.valueOf(ipPayload.getSourceAddress());
        Ip4Address targetIP4Address = Ip4Address.valueOf(ipPayload.getDestinationAddress());
        log.info("External to SDN IPDST = {}", targetIP4Address.toString() + "/32");

        if (containInPrefix(IpPrefix.valueOf("192.168.50.0/24"), targetIP4Address)) { // External to SDN
            Host targetHost = hostService.getHostsByIp(targetIP4Address).iterator().next();
            FilteredConnectPoint ingressFilterPoint = new FilteredConnectPoint(pkt.receivedFrom());
            log.info("External to SDN ingress = {}", ingressFilterPoint);
            FilteredConnectPoint egressFilterPoint
                = new FilteredConnectPoint(
                    new ConnectPoint(targetHost.location().deviceId(), targetHost.location().port()));
            log.info("External to SDN egress = {}", egressFilterPoint);

            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf(targetIP4Address.toString() + "/32"))
                .matchEthType(Ethernet.TYPE_IPV4);
            log.info("External to SDN selector = {}", selector.build());
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                .setEthSrc(MacAddress.valueOf(vRouterConfig.virtualmac()))
                .setEthDst(targetHost.mac());
            log.info("External to SDN treatment = {}", treatment.build());

            PointToPointIntent pointIntent = PointToPointIntent.builder()
                .appId(appId).priority(200)
                .filteredIngressPoint(ingressFilterPoint).filteredEgressPoint(egressFilterPoint)
                .selector(selector.build())
                .treatment(treatment.build())
                .build();

            intentService.submit(pointIntent);

        } else { // SDN to External
            ResolvedRoute targetResolvedRoute = getResolvedRoute(targetIP4Address);
            if (targetResolvedRoute != null) {

                FilteredConnectPoint ingressFilterPoint = new FilteredConnectPoint(pkt.receivedFrom());
                log.info("SDN to External ingress = {}", ingressFilterPoint);
                int index = getIndexInQuageIp(IpPrefix.valueOf(targetResolvedRoute.nextHop(), 24));

                FilteredConnectPoint egressFilterPoint
                    = new FilteredConnectPoint(intfService
                    .getMatchingInterface(IpAddress.valueOf(quagaIP.get(index))).connectPoint());
                log.info("SDN to External egress = {}", egressFilterPoint);

                TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchIPDst(IpPrefix.valueOf(targetIP4Address.toString() + "/32"))
                    .matchEthType(Ethernet.TYPE_IPV4);
                log.info("SDN to External selector = {}", selector.build());
                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(MacAddress.valueOf(vRouterConfig.quagamac()))
                    .setEthDst(targetResolvedRoute.nextHopMac());
                log.info("SDN to External treatment = {}", treatment.build());

                PointToPointIntent pointIntent = PointToPointIntent.builder()
                    .appId(appId).priority(200)
                    .filteredIngressPoint(ingressFilterPoint).filteredEgressPoint(egressFilterPoint)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .build();

                intentService.submit(pointIntent);
            }
        }

    }

    private void installTransiantRule() {
            Collection<RouteTableId> rTable = routeService.getRouteTables();
            ArrayList<RouteTableId> rTableList = new ArrayList<RouteTableId>(rTable);
            Collection<RouteInfo> rinfosC = routeService.getRoutes(rTableList.get(0));
            ArrayList<RouteInfo> rinfos = new ArrayList<RouteInfo>(rinfosC);
            //log.info("CollectionRouteTable = {}", rTable);
            //log.info("ArrayListRouteTable = {}", rTableList);
            //log.info("CollectionRouteInfo = {}", rinfosC);
            //log.info("ArrayListRouteInfo = {}", rinfos);
            //log.info("RouteEvent = {}", event.toString());

            log.info("InfoArraySize = {}", rinfos.size());
            for (int i = 0; i < rinfos.size(); i++) {
                ResolvedRoute resolvedRoute = rinfos.get(i).bestRoute().get();
                Intent existIntent = intentService.getIntent(Key.of(resolvedRoute.prefix().toString(), appId));
                if (existIntent == null) {
                    MacAddress getFromHostService = hostService.
                        getHostsByIp(resolvedRoute.nextHop()).iterator().next().mac();
                    TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                        .matchIPDst(resolvedRoute.prefix()).matchEthType(Ethernet.TYPE_IPV4);
                    log.info("Selector = {}", selector.build());
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                        .setEthSrc(MacAddress.valueOf(vRouterConfig.quagamac()))
                        .setEthDst(resolvedRoute.nextHopMac());
                    log.info("Treatment = {}", treatment.build());

                    log.info("Get from HostService = {}", getFromHostService);
                    log.info("Get from RouteServiec, prefix = {}, next hop = {}, next hop mac = {}",
                            resolvedRoute.prefix(), resolvedRoute.nextHop(), resolvedRoute.nextHopMac());

                    ConnectPoint egressPoint = intfService.getMatchingInterface(resolvedRoute.nextHop()).connectPoint();
                    FilteredConnectPoint egressFilterPoint = new FilteredConnectPoint(egressPoint);
                    log.info("EgressPoint = {}", egressFilterPoint);

                    Set<FilteredConnectPoint> s = new HashSet<FilteredConnectPoint>();
                    for (int j = 0; j < quagaIP.size(); j++) {
                        ConnectPoint ingressPoint = intfService.
                            getMatchingInterface(Ip4Address.valueOf(quagaIP.get(j))).connectPoint();
                        if (!egressPoint.equals(ingressPoint)) {
                            s.add(new FilteredConnectPoint(ingressPoint));
                        }
                    }
                    log.info("IngressPoints = {}", s);

                    Key key = Key.of(resolvedRoute.prefix().toString(), appId);

                    MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                        .appId(appId).key(key)
                        .filteredIngressPoints(s).filteredEgressPoint(egressFilterPoint)
                        .selector(selector.build()).treatment(treatment.build())
                        .build();
                    log.info("Success Build Intent");

                    intentService.submit(intent);
                    log.info("Success Submit Intent");
                }
            }
    }

    private ResolvedRoute getResolvedRoute(Ip4Address ip4Address) {
        Collection<RouteTableId> rTable = routeService.getRouteTables();
        ArrayList<RouteTableId> rTableList = new ArrayList<RouteTableId>(rTable);
        Collection<RouteInfo> rinfosC = routeService.getRoutes(rTableList.get(0));
        ArrayList<RouteInfo> rinfos = new ArrayList<RouteInfo>(rinfosC);

        for (int i = 0; i < rinfos.size(); i++) {
            ResolvedRoute resolvedRoute = rinfos.get(i).bestRoute().get();
            if (resolvedRoute.prefix().contains(ip4Address)) {
                return resolvedRoute;
            }
        }

        return null;
    }

    private int getIndexInQuageIp(IpPrefix ipPrefix) {
        for (int i = 0; i < quagaIP.size(); i++) {
            log.info("IpPrefix = {}", ipPrefix);
            log.info("Prefix contain IP = {}", quagaIP.get(i));
            log.info("------------------");
            if (ipPrefix.contains(IpAddress.valueOf(quagaIP.get(i)))) {

                return i;
            }
        }

        return 0;
    }

    private boolean containInPrefix(IpPrefix ipPrefix, IpAddress ipAddress) {

        if (ipPrefix.contains(ipAddress)) {
            return true;
        }

        return false;
    }

    private boolean containOutPrefix(Ip4Address ip4Address) {

        Collection<RouteTableId> rTable = routeService.getRouteTables();
        ArrayList<RouteTableId> rTableList = new ArrayList<RouteTableId>(rTable);
        Collection<RouteInfo> rinfosC = routeService.getRoutes(rTableList.get(0));
        ArrayList<RouteInfo> rinfos = new ArrayList<RouteInfo>(rinfosC);

        for (int i = 0; i < rinfos.size(); i++) {
            ResolvedRoute resolvedRoute = rinfos.get(i).bestRoute().get();
            if (resolvedRoute.prefix().contains(ip4Address)) {
                return true;
            }
        }

        return false;
    }

    private class VRouterListener implements RouteListener {

        @Override
        public void event(RouteEvent event) {
            installTransiantRule();
        }
    }

    private class VRouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
          if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
              && event.configClass().equals(VRouterConfig.class)) {
            VRouterConfig config = networkConfigRegistry.getConfig(appId, VRouterConfig.class);

            if (config != null) {
                setPeers();
                installBgpRule();
            }
          }
        }
    }

}

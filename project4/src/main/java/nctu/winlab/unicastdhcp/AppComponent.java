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
package nctu.winlab.unicastdhcp;


import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
//import org.onlab.packet.MacAddress;
//import org.onlab.packet.IPv4;
//import org.onlab.packet.TpPort;
//import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
//import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
//import org.onosproject.net.ElementId;
import org.onosproject.net.FilteredConnectPoint;
//import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
//import org.onosproject.net.Host;
//import org.onosproject.net.HostId;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.Intent;
//import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
//import org.onosproject.net.intent.IntentState;
//import org.onosproject.net.config.Config;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
//import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.util.Dictionary;
//import java.util.LinkedList;
//import java.util.List;
import java.util.Properties;

//import static org.onlab.util.Tools.allOf;

//import javax.ws.rs.core.Link;

import static org.onlab.util.Tools.get;

/*
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {


    private final Logger log = LoggerFactory.getLogger(getClass());
    private final DhcpConfigListener cfgListener = new DhcpConfigListener();
    private final ConfigFactory<ApplicationId, DhcpConfig> factory =
    new ConfigFactory<ApplicationId, DhcpConfig>(
        APP_SUBJECT_FACTORY, DhcpConfig.class, "UnicastDhcpConfig") {
      @Override
      public DhcpConfig createConfig() {
        return new DhcpConfig();
      }
    };

    private String[] dhcpServerInfo;

    /** Some configurable property. */
    private ApplicationId appId;
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private UnicastDhcpProcessor processor = new UnicastDhcpProcessor();

    @Activate
    protected void activate() {
        //cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        //cfgService.unregisterProperties(getClass(), false);
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        Iterable<Intent> intents = intentService.getIntentsByAppId(appId);
        for (Intent intent : intents) {
            intentService.withdraw(intent);
            intentService.purge(intent);
        }
        log.info("Stopped");
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

    private class UnicastDhcpProcessor implements PacketProcessor {

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


            MacAddress srd = ethPkt.getSourceMAC();
            MacAddress dst = ethPkt.getDestinationMAC();

            setUpConnectivity(context, srd, dst);
        }
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    private void setUpConnectivity(PacketContext context, MacAddress src, MacAddress dst) {
        //TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchEthSrc(src)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

        TrafficSelector.Builder selector2 = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchEthDst(src)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        Key key, key2;
        key = Key.of(src.toString() + dst.toString(), appId);
        key2 = Key.of(dst.toString() + src.toString(), appId);

        InboundPacket pkt = context.inPacket();
        ConnectPoint ingressPoint = new ConnectPoint(pkt.receivedFrom().deviceId(), pkt.receivedFrom().port());
        FilteredConnectPoint filterIngressPoint = new FilteredConnectPoint(ingressPoint);

        DeviceId dhcpServerId = DeviceId.deviceId(dhcpServerInfo[0]);
        PortNumber dhcpServerPort = PortNumber.portNumber(dhcpServerInfo[1]);
        ConnectPoint egrConnectPoint = new ConnectPoint(dhcpServerId, dhcpServerPort);
        FilteredConnectPoint filterEgressPoint = new FilteredConnectPoint(egrConnectPoint);

        PointToPointIntent pointIntent = PointToPointIntent.builder()
            .appId(appId)
            .key(key)
            .filteredIngressPoint(filterIngressPoint)
            .filteredEgressPoint(filterEgressPoint)
            .selector(selector.build())
            .treatment(treatment)
            .suggestedPath(null)
            .build();

        PointToPointIntent pointIntentReverse = PointToPointIntent.builder()
            .appId(appId)
            .key(key2)
            .filteredIngressPoint(filterEgressPoint)
            .filteredEgressPoint(filterIngressPoint)
            .selector(selector2.build())
            .treatment(treatment)
            .build();

        if (intentService.getIntent(key) != null) {
            return;
        }
        intentService.submit(pointIntent);
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
            filterIngressPoint.connectPoint().deviceId(),
            filterIngressPoint.connectPoint().port(),
            filterEgressPoint.connectPoint().deviceId(),
            filterEgressPoint.connectPoint().port());

        if (intentService.getIntent(key2) != null) {
            return;
        }
        intentService.submit(pointIntentReverse);
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
            filterEgressPoint.connectPoint().deviceId(),
            filterEgressPoint.connectPoint().port(),
            filterIngressPoint.connectPoint().deviceId(),
            filterIngressPoint.connectPoint().port());

    }

    private class DhcpConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
          if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
              && event.configClass().equals(DhcpConfig.class)) {
            DhcpConfig config = cfgService.getConfig(appId, DhcpConfig.class);

            if (config != null) {
                dhcpServerInfo = config.serverLocation().split("/");
                log.info("DHCP server is connected to `{}`, port `{}`",
                 dhcpServerInfo[0],
                 dhcpServerInfo[1]);
            }
          }
        }
    }



}
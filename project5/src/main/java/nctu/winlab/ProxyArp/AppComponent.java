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
package nctu.winlab.ProxyArp;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import javassist.compiler.ast.Pair;

import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
// import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Mac;

// import javax.imageio.IIOParam;
// import javax.xml.stream.util.EventReaderDelegate;

import org.onosproject.net.edge.EdgePortService;
// import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;

import java.util.HashMap;

import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.PortAdmin;
import org.apache.felix.resolver.Util;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
// import org.onlab.packet.IPacket;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;

import static org.onlab.util.Tools.get;

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
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgeService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY)
    //protected HostService hostService;

    private ProxyArpProcessor processor = new ProxyArpProcessor();

    private ApplicationId appId;
    private HashMap<Ip4Address, MacAddress> arpTable;

    private HashMap<MacAddress, ConnectPoint> cpTable;
    

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
        log.info("Started");

        packetService.addProcessor(processor, PacketProcessor.director(1));
        arpTable = new HashMap<Ip4Address, MacAddress>();

        cpTable = new HashMap<MacAddress, ConnectPoint>();
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");

        packetService.removeProcessor(processor);
        processor = null;

        arpTable = null;
        cpTable = null;
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

    private class ProxyArpProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            if (isControlPacket(ethPkt)) {
                return;
            }

            if (ethPkt.getDestinationMAC().isLldp()) {
                return;
            }

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                log.info("type = {}, reply type = {}", Ethernet.TYPE_ARP, ethPkt.getEtherType());
                return;
            }

            MacAddress srcMac = ethPkt.getSourceMAC();
            ARP arpPayload =  (ARP) ethPkt.getPayload();
            Ip4Address srcIP4Address = Ip4Address.valueOf(arpPayload.getSenderProtocolAddress());
            if (!arpTable.containsKey(srcIP4Address)) {
                arpTable.put(srcIP4Address, srcMac);
            }

            if (!cpTable.containsKey(srcMac)) {
                cpTable.put(srcMac, pkt.receivedFrom());
            }
            
            arpHandler(context);
        }
    }

    private void arpHandler(PacketContext context) {

        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        ARP arpPayload = (ARP) ethPkt.getPayload();
        Ip4Address dstIp4Address = Ip4Address.valueOf(arpPayload.getTargetProtocolAddress());

        if (arpPayload.getOpCode() == ARP.OP_REQUEST) {

            if (arpTable.containsKey(dstIp4Address)) {
                MacAddress dstMac = arpTable.get(dstIp4Address);
                Ethernet arpPacket = ARP.buildArpReply(dstIp4Address, dstMac, ethPkt);
                packetOut(pkt.receivedFrom().deviceId(), pkt.receivedFrom().port(),
                            ByteBuffer.wrap(arpPacket.serialize()));
                log.info("TABLE HIT. Requested MAC = {}", dstMac);
            } else {
                for (ConnectPoint cp : edgeService.getEdgePoints()) {
                    if (!pkt.receivedFrom().deviceId().equals(cp.deviceId()) ||
                    !pkt.receivedFrom().port().equals(cp.port())) {
                        packetOut(cp.deviceId(), cp.port(), ByteBuffer.wrap(ethPkt.serialize()));

                    }
                }

                log.info("TABLE MISS. Send request to edge ports");
            } 
        } else if (arpPayload.getOpCode() == ARP.OP_REPLY) {
            // maybe have a bug
            // Set<Host> dstHost = hostService.getHostsByMac(MacAddress.valueOf(arpPayload.getTargetHardwareAddress()));
            //sometime hostservice haven't update host information, so it will not get host
            // if (dstHost.isEmpty()) {
            //     log.info("dstHost is Empty {}", MacAddress.valueOf(arpPayload.getTargetHardwareAddress()));
            //     return;
            // }

            // for (Host host : dstHost) {
            //     packetOut(host.location().deviceId(), host.location().port(), ByteBuffer.wrap(ethPkt.serialize()));
            //     log.info("RECV REPLY. Request MAC = {}",
            //     MacAddress.valueOf(arpPayload.getSenderHardwareAddress()));
            // }
            MacAddress dstMac = MacAddress.valueOf(arpPayload.getTargetHardwareAddress());
            if (!cpTable.containsKey(dstMac)) {
                log.info("didn't find target host");
                return;
            }
            packetOut(cpTable.get(dstMac).deviceId(), cpTable.get(dstMac).port(), ByteBuffer.wrap(ethPkt.serialize()));
            log.info("RECV REPLY. Request MAC = {}",
                 MacAddress.valueOf(arpPayload.getSenderHardwareAddress()));
        }
    }

    private void packetOut(DeviceId targetDeviceId, PortNumber portNumber, ByteBuffer pktData) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(portNumber).build();
        OutboundPacket outboundPacket = new DefaultOutboundPacket(targetDeviceId, treatment, pktData);

        packetService.emit(outboundPacket);
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

}

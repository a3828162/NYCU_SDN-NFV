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
package nctu.winlab.bridge;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
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

import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.DeviceId;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Properties;

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
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    private PacketForwardProcessor processor = new PacketForwardProcessor();

    private ApplicationId appId;

    private HashMap<DeviceId, HashMap<MacAddress, PortNumber>> deviceMetrice;


    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.winlab.bridge");
        log.info("Started");
        packetService.addProcessor(processor, PacketProcessor.director(1));


        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        deviceMetrice = new HashMap<DeviceId, HashMap<MacAddress, PortNumber>>();
    }

    @Deactivate
    protected void deactivate() {

        cfgService.unregisterProperties(getClass(), false);
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        deviceMetrice = null;
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

    private class PacketForwardProcessor implements PacketProcessor {
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

            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            // Consider 3 cituation
            // 1. mac table didn't have device table
            // 2. device table didn't have its srcmac address
            // 3. find srcmac address in table, but port didn't same
            if (!deviceMetrice.containsKey(pkt.receivedFrom().deviceId())
            || !deviceMetrice.get(pkt.receivedFrom().deviceId()).containsKey(srcMac)
            || !deviceMetrice.get(pkt.receivedFrom().deviceId()).get(srcMac).equals(pkt.receivedFrom().port())) {
                log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`.",
                pkt.receivedFrom().deviceId(),
                srcMac,
                pkt.receivedFrom().port());
                if (!deviceMetrice.containsKey(pkt.receivedFrom().deviceId())) {
                    HashMap<MacAddress, PortNumber> tmp = new HashMap<MacAddress, PortNumber>();
                    tmp.put(srcMac, pkt.receivedFrom().port());
                    deviceMetrice.put(pkt.receivedFrom().deviceId(), tmp);
                } else {
                    deviceMetrice.get(pkt.receivedFrom().deviceId()).put(srcMac, pkt.receivedFrom().port());
                }
            }

            // dstmac didn't find in mac table
            if (!deviceMetrice.containsKey(pkt.receivedFrom().deviceId())
            || !deviceMetrice.get(pkt.receivedFrom().deviceId()).containsKey(dstMac)) {
                log.info("MAC address `{}` is missed on `{}`. Flood the packet.",
                dstMac,
                pkt.receivedFrom().deviceId());
                flood(context);
                return;
            }

            // dstmac address find in table, packet it out and install a rule
            packetOut(context, deviceMetrice.get(pkt.receivedFrom().deviceId()).get(dstMac));
            log.info("MAC address `{}` is matched on `{}`. Install a flow rule.",
            dstMac,
            pkt.receivedFrom().deviceId());
            installFlowRule(context, srcMac, dstMac);
        }
    }

    private void installFlowRule(PacketContext context, MacAddress srcMac, MacAddress dstMac) {

        InboundPacket pkt = context.inPacket();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        selectorBuilder.matchEthSrc(srcMac)
                .matchEthDst(dstMac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(deviceMetrice.get(pkt.receivedFrom().deviceId()).get(dstMac))
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(30)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(30)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);

        return;
    }

    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }

    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

}

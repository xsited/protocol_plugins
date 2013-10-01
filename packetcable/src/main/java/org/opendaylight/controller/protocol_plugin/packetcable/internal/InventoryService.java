package org.opendaylight.controller.protocol_plugin.packetcable.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.felix.dm.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.sal.core.Actions;
import org.opendaylight.controller.sal.core.Bandwidth;
import org.opendaylight.controller.sal.core.Buffers;
import org.opendaylight.controller.sal.core.Capabilities;
import org.opendaylight.controller.sal.core.Capabilities.CapabilitiesType;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.core.State;
import org.opendaylight.controller.sal.core.Tables;
import org.opendaylight.controller.sal.core.TimeStamp;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.sal.inventory.IPluginInInventoryService;
import org.opendaylight.controller.sal.inventory.IPluginOutInventoryService;
import org.opendaylight.controller.sal.utils.NodeCreator;
import org.opendaylight.controller.sal.utils.NodeConnectorCreator;

/**
 * PC Implementation for IPluginInReadService used by SAL
 *
 *
 */
public class InventoryService implements IPluginInInventoryService {
    private static final Logger logger = LoggerFactory
            .getLogger(InventoryService.class);

    private ConcurrentMap<Node, Map<String, Property>> nodeProps; // properties
                                                                  // are
                                                                  // maintained
                                                                  // in global
                                                                  // container
                                                                  // only
    private ConcurrentMap<NodeConnector, Map<String, Property>> nodeConnectorProps; // properties
                                                                                    // are
                                                                                    // maintained
                                                                                    // in
                                                                                    // global
                                                                                    // container
                                                                                    // only
    private final Set<IPluginOutInventoryService> pluginOutInventoryServices =
            new CopyOnWriteArraySet<IPluginOutInventoryService>();

    public void setPluginOutInventoryServices(IPluginOutInventoryService service) {
        logger.trace("Got a service set request {}", service);
        if (this.pluginOutInventoryServices != null) {
            this.pluginOutInventoryServices.add(service);
        }
    }

    public void unsetPluginOutInventoryServices(IPluginOutInventoryService service) {
        logger.trace("Got a service UNset request");
        if (this.pluginOutInventoryServices != null) {
            this.pluginOutInventoryServices.remove(service);
        }
    }

    /**
     * Function called by the dependency manager when all the required
     * dependencies are satisfied
     *
     */
    void init() {
        nodeProps = new ConcurrentHashMap<Node, Map<String, Property>>();
        nodeConnectorProps = new ConcurrentHashMap<NodeConnector, Map<String, Property>>();
        Node.NodeIDType.registerIDType("PC", Integer.class);
        NodeConnector.NodeConnectorIDType.registerIDType("PC", Integer.class, "PC");

        setupNodeProps();
        setupNodeConnectorProps();
    }

    private void setupNodeConnectorProps() {
        Map<String, Property> ncPropMap = new HashMap<String, Property>();
        Capabilities cap = new Capabilities(CapabilitiesType.FLOW_STATS_CAPABILITY.getValue());
        ncPropMap.put(Capabilities.CapabilitiesPropName, cap);
        Bandwidth bw = new Bandwidth(Bandwidth.BW1Gbps);
        ncPropMap.put(Bandwidth.BandwidthPropName, bw);
        State st = new State(State.EDGE_UP);
        ncPropMap.put(State.StatePropName, st);

        // setup property map for all node connectors
        NodeConnector nc;
        Node node;
        try {
            node = new Node("PC", new Integer(0xCAFE));
            nc = new NodeConnector("PC", 0xCAFE, node);
        } catch (ConstructionException e) {
            nc = null;
            node = null;
        }
        nodeConnectorProps.put(nc, ncPropMap);
/*

        try {
            node = new Node("PC", 3366);
            nc = new NodeConnector("PC", 12, node);
        } catch (ConstructionException e) {
            nc = null;
            node = null;
        }
        nodeConnectorProps.put(nc, ncPropMap);

        try {
            node = new Node("PC", 4477);
            nc = new NodeConnector("PC", 34, node);
        } catch (ConstructionException e) {
            nc = null;
            node = null;
        }
        nodeConnectorProps.put(nc, ncPropMap);
*/
    }

    private void setupNodeProps() {
        Map<String, Property> propMap = new HashMap<String, Property>();

        Tables t = new Tables((byte) 1);
        propMap.put(Tables.TablesPropName, t);
        Capabilities c = new Capabilities((int) 3);
        propMap.put(Capabilities.CapabilitiesPropName, c);
        Actions a = new Actions((int) 2);
        propMap.put(Actions.ActionsPropName, a);
        Buffers b = new Buffers((int) 1);
        propMap.put(Buffers.BuffersPropName, b);
        Long connectedSinceTime = 100000L;
        TimeStamp timeStamp = new TimeStamp(connectedSinceTime, "connectedSince");
        propMap.put(TimeStamp.TimeStampPropName, timeStamp);

        // setup property map for all nodes
        Node node;
        try {
            node = new Node("PC", new Integer(0xCAFE));
        } catch (ConstructionException e) {
            node = null;
        }

        nodeProps.put(node, propMap);
/*
        try {
            node = new Node("PC", 3366);
        } catch (ConstructionException e) {
            node = null;
        }
        nodeProps.put(node, propMap);

        try {
            node = new Node("PC", 4477);
        } catch (ConstructionException e) {
            node = null;
        }
        nodeProps.put(node, propMap);

*/
    }

    /**
     * Function called by the dependency manager when at least one dependency
     * become unsatisfied or when the component is shutting down because for
     * example bundle is being stopped.
     *
     */
    void destroy() {
    }

    /**
     * Function called by dependency manager after "init ()" is called and after
     * the services provided by the class are registered in the service registry
     *
     */
    void start() {
    }

    /**
     * Method called when the plugin has exposed it's services, this will be
     * used to publish the updates so connection manager can think the
     * connection is local
     */
    void started() {
        // update sal and discovery
        for (IPluginOutInventoryService service : pluginOutInventoryServices) {
            for (Node node : nodeProps.keySet()) {
                Set<Property> props = new HashSet<Property>(nodeProps.get(node).values());
                service.updateNode(node, UpdateType.ADDED, props);
                logger.trace("Adding Node {} with props {}", node, props);
            }
            for (NodeConnector nc : nodeConnectorProps.keySet()) {
                Set<Property> props = new HashSet<Property>(nodeConnectorProps.get(nc).values());
                service.updateNodeConnector(nc, UpdateType.ADDED, props);
                logger.trace("Adding NodeConnectors {} with props {}", nc, props);
            }
        }
    }

    /**
     * Function called by the dependency manager before the services exported by
     * the component are unregistered, this will be followed by a "destroy ()"
     * calls
     *
     */
    void stop() {
        pluginOutInventoryServices.clear();
    }

    /**
     * Retrieve nodes from openflow
     */
    @Override
    public ConcurrentMap<Node, Map<String, Property>> getNodeProps() {
        return nodeProps;
    }

    /**
     * Retrieve nodeConnectors from openflow
     */
    @Override
    public ConcurrentMap<NodeConnector, Map<String, Property>> getNodeConnectorProps(
            Boolean refresh) {
        return nodeConnectorProps;
    }

}

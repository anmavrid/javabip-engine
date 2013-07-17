package org.bip.engine;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * @author mavridou
 */

/** Computes the BDD of the behaviour of all components */
public class BehaviourEncoderImpl implements BehaviourEncoder {

	private Logger logger = LoggerFactory.getLogger(BehaviourEncoderImpl.class);
	//TODO: pass the portBDDs, (stateBDDs ?) to the BDDEngine
	private volatile Hashtable<BIPComponent, BDD[]> stateBDDs = new Hashtable<BIPComponent, BDD[]>();
	private volatile Hashtable<BIPComponent, BDD[]> portBDDs = new Hashtable<BIPComponent, BDD[]>();
	//TODO: move the portToBDDs to the BDDEngine to simplify the dependencies
	private Hashtable <BIPComponent, Hashtable<String, BDD>> componentToPortToBDDs = new Hashtable <BIPComponent, Hashtable<String,BDD>>();
	private int auxSum;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;
	
	/**
	 * Creates one-node BDDs for the states and the ports of all components.
	 * 
	 * @param The component that we want to create the BDDs nodes for
	 * @param The ports of the component 
	 * @param The states of the component
	 * 
	 * @throws BIPEngineExc	eption
	 */
	public synchronized void createBDDNodes(BIPComponent component, ArrayList<Port> componentPorts, ArrayList<String> componentStates) throws BIPEngineException {

		int nbComponentPorts = componentPorts.size();
		int nbComponentStates = componentStates.size();
		int initialNoNodes = nbComponentPorts + nbComponentStates + auxSum;
		
		if (engine.getBDDManager().varNum() < initialNoNodes){
			engine.getBDDManager().setVarNum(initialNoNodes);
		}
		
		BDD[] singleNodeBDDsForStates = new BDD[nbComponentStates];
		for (int i = 0; i < nbComponentStates; i++) {
			
			/*Create new variable in the BDD manager for the state of each component instance.*/
			singleNodeBDDsForStates[i] = engine.getBDDManager().ithVar(i + auxSum);
			if (singleNodeBDDsForStates[i] == null){
				try {
					logger.error("BDD node that corresponds to the state {} of component {} is not created.", componentStates.get(i), component.getName());
					throw new BIPEngineException("BDD node that corresponds to a state is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
		}
		stateBDDs.put(component, singleNodeBDDsForStates);

		BDD[] singleNodeBDDsForPorts = new BDD[nbComponentPorts];
		Hashtable <String, BDD> portToBDD = new Hashtable<String, BDD>();
		for (int j = 0; j < nbComponentPorts; j++) {
			
			/*Create new variable in the BDD manager for the port of each component instance.*/
			singleNodeBDDsForPorts[j] = engine.getBDDManager().ithVar(j + nbComponentStates + auxSum);
			if (singleNodeBDDsForPorts[j] == null){
				try {
					logger.error("Single node BDD for port {} is equal to null", componentPorts.get(j));
					throw new BIPEngineException("Single node BDD for port is equal to null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
			portToBDD.put(componentPorts.get(j).id, singleNodeBDDsForPorts[j]);
			if (portToBDD.get(componentPorts.get(j).id) == null){
				try {
					logger.error("BDD node that corresponds to the port {} of component {} is not created.", componentPorts.get(j).id, component.getName());
					throw new BIPEngineException("BDD node that corresponds to a port is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
		}
		componentToPortToBDDs.put(component, portToBDD);
		portBDDs.put(component, singleNodeBDDsForPorts);
		auxSum = auxSum + nbComponentPorts + nbComponentStates;
	}

	/** 
	 * Computes the Behavior BDD of a component 
	 */
	public synchronized BDD behaviourBDD(BIPComponent component) {
		BDD componentBehaviour = engine.getBDDManager().zero(); // for OR-ing
		
		Behaviour behaviour = wrapper.getBehaviourByComponent(component);
		
		ArrayList<Port> componentPorts = (ArrayList<Port>) behaviour.getEnforceablePorts();
		ArrayList<String> componentStates = (ArrayList<String>) behaviour.getStates();

		int nbStates = componentStates.size();
		int nbPorts = componentPorts.size();

		Hashtable<String, ArrayList<Port>> statePorts = new Hashtable<String, ArrayList<Port>>();
		statePorts = (Hashtable<String, ArrayList<Port>>) behaviour.getStateToPorts();
		
		int c_size = 0;
		for (Map.Entry<String, ArrayList<Port>> entry : statePorts.entrySet()) {
			c_size = c_size + entry.getValue().size();
			if (entry.getValue().size() == 0) {
				c_size++;
			}
		}
		
		BDD[] c = new BDD[c_size + 1];
		ArrayList<Port> portsValue = new ArrayList<Port>();
		String stateKey;

		ArrayList<Integer> availablePorts = new ArrayList<Integer>();

		for (Map.Entry<String, ArrayList<Port>> entry : statePorts.entrySet()) {
			portsValue = entry.getValue();
			stateKey = entry.getKey();
			for (int i = 0; i < portsValue.size(); i++) {
				int j = 0;
				while (portsValue.get(i) != componentPorts.get(j)) {
					if (j == componentPorts.size() - 1) {
						try {
							//TODO: Change error message
							throw new BIPEngineException("Port not found.");
						} catch (BIPEngineException e) {
							e.printStackTrace();
							logger.error(e.getMessage());	
						} 
					}
					j++;
				}
				if (portsValue.get(i) == componentPorts.get(j))
					availablePorts.add(j + 1);
			}
			int aux = 0;
			for (int i = 0; i < componentStates.size(); i++) {
				if (stateKey.equals(componentStates.get(i))) {
					aux = i + 1;
					break;
				}
			}

			for (int i = 0; i < portsValue.size(); i++) {
				BDD aux1 = engine.getBDDManager().one();
				for (int j = 1; j <= nbStates; j++) {
					if (aux == j)
						c[aux + i - 1] = aux1.and(stateBDDs.get(component)[j-1]);
					else
						c[aux + i - 1] = aux1.and(stateBDDs.get(component)[j-1].not());
					if (j != nbStates) {
						aux1.free();
						aux1 = c[aux + i - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[aux + i - 1];
				for (int j = 1; j <= nbPorts; j++) {
					if (availablePorts.get(i) == j)
						c[aux + i - 1] = aux2.and(portBDDs.get(component)[j-1]);
					else
						c[aux + i - 1] = aux2.and(portBDDs.get(component)[j-1].not());
					if (j != nbPorts) {
						aux2.free();
						aux2 = c[aux + i - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[aux + i - 1]);
			}

			if (portsValue.size() == 0) {

				BDD aux1 = engine.getBDDManager().one();
				for (int i = 1; i <= nbStates; i++) {
					if (aux == i)
						c[aux - 1] = aux1.and(stateBDDs.get(component)[i-1]);
					else
						c[aux - 1] = aux1.and(stateBDDs.get(component)[i-1].not());
					if (i != nbStates) {
						aux1.free();
						aux1 = c[aux - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[aux - 1];
				for (int i = 1; i <= nbPorts; i++) {
					c[aux - 1] = aux2.and(portBDDs.get(component)[i-1].not());
					if (i != nbPorts) {
						aux2.free();
						aux2 = c[aux - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[aux - 1]);

			}

			availablePorts.clear();

		}

		BDD aux3 = engine.getBDDManager().one();
		for (int j = 1; j <= nbPorts; j++) {
			c[c.length - 1] = aux3.and(portBDDs.get(component)[j-1].not());
			if (j != nbPorts) {
				aux3.free();
				aux3 = c[c.length - 1];
			}
		}
		aux3.free();
		return componentBehaviour.orWith(c[c.length - 1]);

	}


	public void setEngine(BDDBIPEngine engine) { 
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public synchronized Hashtable<BIPComponent, BDD[]> getStateBDDs() {
		return stateBDDs;
	}

	public synchronized Hashtable<BIPComponent, BDD[]> getPortBDDs() {
		return portBDDs;
	}
	
	public synchronized BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException {
		Hashtable<String, BDD> aux = componentToPortToBDDs.get(component);
		if (aux.get(portName) == null){
			try {
				logger.error("BDD node of port {} is null", portName);
				throw new BIPEngineException("BDD node of a port is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return aux.get(portName);
	}


	
	

}

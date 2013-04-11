package org.bip.engine;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.behaviour.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Computes the BDD of the behaviour of all components */
public class BehaviourEncoderImpl implements BehaviourEncoder {

	private volatile Hashtable<Integer, BDD[]> stateBDDs = new Hashtable<Integer, BDD[]>();
	private volatile Hashtable<Integer, BDD[]> portBDDs = new Hashtable<Integer, BDD[]>();
	private int auxSum;

	private BDDBIPEngineImpl engine; //TODO, use the IFs instead

	private OSGiBIPEngine wrapper;

	private Logger logger = LoggerFactory.getLogger(BehaviourEncoderImpl.class);

	public void setEngine(BDDBIPEngineImpl engine) {  //TODO, use the IFs instead
		this.engine = engine;
	}

	public void setOSGiBIPEngine(OSGiBIPEngine wrapper) {
		this.wrapper = wrapper;
	}

	protected synchronized Hashtable<Integer, BDD[]> getStateBDDs() {
		return stateBDDs;
	}

	protected synchronized Hashtable<Integer, BDD[]> getPortBDDs() {
		return portBDDs;
	}

	private synchronized void createPortAndStateBDDs(int componentID, int sum, int noStates, int noPorts) {
		BDD[] a2 = new BDD[noStates];
		for (int i = 0; i < noStates; i++) {
			/**
			 * create new variable in the BDD manager for the state of each
			 * component instance
			 */
			a2[i] = engine.bdd_mgr.ithVar(i + sum);
		}
		stateBDDs.put(componentID, a2);

		BDD[] a1 = new BDD[noPorts];
		for (int j = 0; j < noPorts; j++) {
			/**
			 * create new variable in the BDD manager for the port of each
			 * component instance
			 */
			a1[j] = engine.bdd_mgr.ithVar(j + noStates + sum);
		}
		portBDDs.put(componentID, a1);
		logger.error("Component {} put to portBdds, size={}. ", componentID, portBDDs.size());
		System.out.println("portBDDs size: " + portBDDs.size());
	}

	/** All the components need to be registered before creating the nodes */
	public synchronized void createBDDNodes(int componentID, int noComponentPorts, int noComponentStates) {

		int initialNoNodes = noComponentPorts + noComponentStates + auxSum;

		// int auxSum = 0;
		logger.error("Initial no of Nodes {}", initialNoNodes);
		logger.error("BDD manager variable Number {}", engine.bdd_mgr.varNum());
		if (engine.bdd_mgr.varNum() < initialNoNodes)
			engine.bdd_mgr.setVarNum(initialNoNodes);

		logger.error("componentID {}, auxSum {}", componentID, auxSum);
		logger.error("noComponentPorts {}, noComponentStates {}", noComponentPorts, noComponentStates);
		createPortAndStateBDDs(componentID, auxSum, noComponentStates, noComponentPorts);
		auxSum = auxSum + noComponentPorts + noComponentStates;

	}

	/** Compute the Behavior BDD of a component */
	private synchronized BDD behaviourBDD(int componentID) {
		BDD componentBehaviour = engine.bdd_mgr.zero();// for OR-ing
		ArrayList<Port> componentPorts = wrapper.behaviourMapping.get(componentID).getEnforceablePorts();
		ArrayList<String> componentStates = wrapper.behaviourMapping.get(componentID).getStates();
		int noStates = wrapper.behaviourMapping.get(componentID).getStates().size();
		int noPorts = wrapper.behaviourMapping.get(componentID).getEnforceablePorts().size();

		BDD[] portsBDDs = new BDD[noPorts];
		BDD[] statesBDDs = new BDD[noStates];

		for (int i = 0; i < noPorts; i++) {
			portsBDDs[i] = portBDDs.get(componentID)[i];
		}
		for (int j = 0; j < noStates; j++) {
			statesBDDs[j] = stateBDDs.get(componentID)[j];
		}

		Hashtable<String, ArrayList<Port>> statePorts = new Hashtable<String, ArrayList<Port>>();
		statePorts = wrapper.behaviourMapping.get(componentID).getStateToPorts();
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
			// portsValue.clear();
			portsValue = entry.getValue();// StatePorts.get(i);
			stateKey = entry.getKey();
			for (int l = 0; l < portsValue.size(); l++) {
				int k = 0;
				while (portsValue.get(l) != componentPorts.get(k)) {
					if (k == componentPorts.size() - 1) {
						System.out.println("Port not found!");
						break;
					}
					k++;
				}
				if (portsValue.get(l) == componentPorts.get(k))
					availablePorts.add(k + 1);
			}
			int i = 0;
			for (int m = 0; m < componentStates.size(); m++) {
				if (stateKey.equals(componentStates.get(m))) { // TODO:
																// algorithmic
																// complexity?
					i = m + 1;
					break;
				}
			}

			for (int l = 0; l < portsValue.size(); l++) {
				BDD aux1 = engine.bdd_mgr.one();
				for (int j = 1; j <= noStates; j++) {
					if (i == j)
						c[i + l - 1] = aux1.and(statesBDDs[j - 1]);
					else
						c[i + l - 1] = aux1.and(statesBDDs[j - 1].not());
					if (j != noStates) {
						aux1.free();
						aux1 = c[i + l - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[i + l - 1];
				for (int j = 1; j <= noPorts; j++) {
					if (availablePorts.get(l) == j)
						c[i + l - 1] = aux2.and(portsBDDs[j - 1]);
					else
						c[i + l - 1] = aux2.and(portsBDDs[j - 1].not());
					if (j != noPorts) {
						aux2.free();
						aux2 = c[i + l - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[i + l - 1]);
			}

			if (portsValue.size() == 0) {

				BDD aux1 = engine.bdd_mgr.one();
				for (int j = 1; j <= noStates; j++) {
					if (i == j)
						c[i - 1] = aux1.and(statesBDDs[j - 1]);
					else
						c[i - 1] = aux1.and(statesBDDs[j - 1].not());
					if (j != noStates) {
						aux1.free();
						aux1 = c[i - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[i - 1];
				for (int j = 1; j <= noPorts; j++) {
					c[i - 1] = aux2.and(portsBDDs[j - 1].not());
					if (j != noPorts) {
						aux2.free();
						aux2 = c[i - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[i - 1]);

			}

			availablePorts.clear();

		}

		BDD aux3 = engine.bdd_mgr.one();
		for (int j = 1; j <= noPorts; j++) {
			c[c.length - 1] = aux3.and(portsBDDs[j - 1].not());
			if (j != noPorts) {
				aux3.free();
				aux3 = c[c.length - 1];
			}
		}
		aux3.free();

		componentBehaviour.orWith(c[c.length - 1]);

		return componentBehaviour;

	}

	public BDD totalBehaviour() {
		/** conjunction of all FSM BDDs (Λi Fi) */
		BDD totalBehaviour = engine.bdd_mgr.one();
		for (int k = 1; k <= wrapper.noComponents; k++) {
			totalBehaviour.andWith(behaviourBDD(k));
		}
		return totalBehaviour;
	}

}
/**
 *  Copyright 2014 Universite Paris Ouest Nanterre & Sorbonne Universites, Univ. Paris 06 - CNRS UMR 7606 (LIP6/MoVe)
 *  All rights reserved.   This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Project leader / Initial Contributor:
 *    Lom Messan Hillah - <lom-messan.hillah@lip6.fr>
 *
 *  Contributors:
 *    ${ocontributors} - <$oemails}>
 *
 *  Mailing list:
 *    lom-messan.hillah@lip6.fr
 */
package fr.lip6.move.pnml2bpn.export.impl;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.bind.ValidationException;

import org.slf4j.Logger;

import com.ximpleware.extended.AutoPilotHuge;
import com.ximpleware.extended.NavExceptionHuge;
import com.ximpleware.extended.ParseExceptionHuge;
import com.ximpleware.extended.VTDGenHuge;
import com.ximpleware.extended.VTDNavHuge;
import com.ximpleware.extended.XMLMemMappedBuffer;
import com.ximpleware.extended.XPathEvalExceptionHuge;
import com.ximpleware.extended.XPathParseExceptionHuge;

import fr.lip6.move.pnml2bpn.exceptions.InternalException;
import fr.lip6.move.pnml2bpn.exceptions.InvalidPNMLTypeException;
import fr.lip6.move.pnml2bpn.exceptions.InvalidSafeNetException;
import fr.lip6.move.pnml2bpn.exceptions.PNMLImportExportException;
import fr.lip6.move.pnml2bpn.export.PNMLExporter;
import fr.lip6.move.pnml2bpn.utils.PNML2BPNUtils;
import fr.lip6.move.pnml2bpn.utils.SafePNChecker;

/**
 * Actual PNML 2 BPN exporter.
 * 
 * @author lom
 * 
 */
public final class PNML2BPNExporter implements PNMLExporter {
	@SuppressWarnings("unused")
	private static final String PNML2BPN_EXT = ".pnml2bpn";
	private static final String TRANS_EXT = ".trans";
	private static final String STATES_EXT = ".states";
	private static final String STOP = "STOP";
	private static final String PLACE_MAPPING_MSG = "Places ID mapping BPN -- PNML";
	private static final String TRANSITIONS_MAPPING_MSG = "Transitions ID mapping BPN -- PNML";
	private static final String NL = "\n";
	private static final String HK = "#";
	private static final String PLACES = "places";
	private static final String UNITS = "units";
	private static final String U = "U";
	private static final String INIT_PLACE = "initial place";
	private static final String INIT_PLACES = "initial places";
	private static final String ROOT_UNIT = "root unit";
	private static final String TRANSITIONS = "transitions";
	private static final String T = "T";
	private static final String WS = " ";
	private static final String ZERO = "0";
	private static final String ONE = "1";
	private static final String DOTS = "...";

	private Logger log = null;

	private Object2LongOpenHashMap<String> placesId2bpnMap = null;

	private Object2LongOpenHashMap<String> trId2bpnMap = null;
	private Long2ObjectOpenHashMap<LongBigArrayBigList> tr2OutPlacesMap = null;
	private Long2ObjectOpenHashMap<LongBigArrayBigList> tr2InPlacesMap = null;
	private File currentInputFile = null;
	private SafePNChecker spnc = null;

	public PNML2BPNExporter() {
		spnc = new SafePNChecker();
	}

	@Override
	public void exportPNML(URI inFile, URI outFile, Logger journal)
			throws PNMLImportExportException, InterruptedException, IOException {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public void exportPNML(File inFile, File outFile, Logger journal)
			throws PNMLImportExportException, InterruptedException, IOException {
		initLog(journal);
		export(inFile, outFile, journal);
	}

	@Override
	public void exportPNML(String inFile, String outFile, Logger journal)
			throws PNMLImportExportException, InterruptedException, IOException {
		initLog(journal);
		export(new File(inFile), new File(outFile), journal);
	}

	/**
	 * @param journal
	 */
	private void initLog(Logger journal) {
		this.log = journal;
	}

	private void export(File inFile, File outFile, Logger journal)
			throws PNMLImportExportException, InterruptedException, IOException {
		initLog(journal);
		try {
			this.currentInputFile = inFile;
			journal.info("Checking preconditions on input file format: {} ",
					inFile.getCanonicalPath());
			PNML2BPNUtils.checkIsPnmlFile(inFile);
			log.info("Exporting into BPN: {}", inFile.getCanonicalPath());
			/*
			 * String sessionId = "pnml2bpn" + Thread.currentThread().getId();
			 * PNMLUtils.createWorkspace(sessionId); HLAPIRootClass rootClass =
			 * PNMLUtils.importPnmlDocument(inFile, false); if
			 * (!PNType.PTNET.equals(PNMLUtils.determineNetType(rootClass))) {
			 * String message =
			 * "This translation tool only support P/T Nets. The PNML you provided does not have a P/T Net root."
			 * ; journal.error(message); throw new
			 * PNMLImportExportException(message); }
			 */
			translateIntoBPN(inFile, outFile, journal);

		} catch (ValidationException
				| fr.lip6.move.pnml2bpn.exceptions.InvalidFileTypeException
				| fr.lip6.move.pnml2bpn.exceptions.InvalidFileException
				| InternalException | InvalidPNMLTypeException e) {
			// journal.error(e.getMessage());
			// MainPNML2BPN.printStackTrace(e);
			throw new PNMLImportExportException(e);
		} catch (IOException e) {
			throw e;
		}

	}

	private void translateIntoBPN(File inFile, File outFile, Logger journal)
			throws InvalidPNMLTypeException, InterruptedException,
			PNMLImportExportException, IOException {
		XMLMemMappedBuffer xb = new XMLMemMappedBuffer();
		VTDGenHuge vg = new VTDGenHuge();
		try {
			xb.readFile(inFile.getCanonicalPath());
			vg.setDoc(xb);
			vg.parse(true);

			VTDNavHuge vn = vg.getNav();

			AutoPilotHuge ap = new AutoPilotHuge(vn);
			log.info("Checking it is a PT Net.");
			if (!isPTNet(ap, vn)) {
				throw new InvalidPNMLTypeException(
						"The contained Petri net(s) in the following file is not a P/T Net. Only P/T Nets are supported: "
								+ this.currentInputFile.getCanonicalPath());
			}
			// The net must be 1-safe.
			log.info("Checking it is 1-Safe.");
			if (!isNet1Safe()) {
				throw new InvalidSafeNetException(
						"The net(s) in the submitted document is not 1-safe: "
								+ this.currentInputFile.getCanonicalPath());
			}
			log.info("Net appears to be 1-Safe.");
			// Open BPN and mapping files channels, and init write queues
			File outTSFile = new File(PNML2BPNUtils.extractBaseName(outFile.getCanonicalPath()) + TRANS_EXT);
			File outPSFile = new File(PNML2BPNUtils.extractBaseName(outFile.getCanonicalPath()) + STATES_EXT);
			// Channels for BPN, transitions and places id mapping
			OutChannelBean ocbBpn = PNML2BPNUtils.openOutChannel(outFile);
			OutChannelBean ocbTs = PNML2BPNUtils.openOutChannel(outTSFile);
			OutChannelBean ocbPs = PNML2BPNUtils.openOutChannel(outPSFile);
			// Queues for BPN, transitions and places id mapping
			BlockingQueue<String> bpnQueue = initQueue();
			BlockingQueue<String> tsQueue = initQueue();
			BlockingQueue<String> psQueue = initQueue();
			// Start writers
			Thread bpnWriter = startWriter(ocbBpn, bpnQueue);
			Thread tsWriter = startWriter(ocbTs, tsQueue);
			Thread psWriter = startWriter(ocbPs, psQueue);

			// Init data type for places id and export places
			initPlacesMap();
			log.info("Exporting places.");
			exportPlacesIntoUnits(ap, vn, bpnQueue, psQueue);

			// Init data type for transitions id and export transitions
			initTransitionsMaps();
			log.info("Exporting transitions.");
			exportTransitions(ap, vn, bpnQueue, tsQueue);

			// Stop Writers
			bpnQueue.put(STOP);
			tsQueue.put(STOP);
			psQueue.put(STOP);
			bpnWriter.join();
			tsWriter.join();
			psWriter.join();
			// Close channels
			PNML2BPNUtils.closeOutChannel(ocbBpn);
			PNML2BPNUtils.closeOutChannel(ocbTs);
			PNML2BPNUtils.closeOutChannel(ocbPs);
			// clear maps
			clearAllMaps();
			log.info("See BPN and mapping files: {}, {} and {}",
					outFile.getCanonicalPath(), outTSFile.getCanonicalPath(), outPSFile.getCanonicalPath());
		} catch (NavExceptionHuge | XPathParseExceptionHuge
				| XPathEvalExceptionHuge | ParseExceptionHuge
				| InvalidSafeNetException | InternalException e) {
			throw new PNMLImportExportException(e);
		} catch (InterruptedException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Clears all internal data structures for places and transitions.
	 */
	private void clearAllMaps() {
		placesId2bpnMap.clear();
		trId2bpnMap.clear();
		tr2InPlacesMap.clear();
		tr2OutPlacesMap.clear();
	}

	/**
	 * Initialize internal data structures for transitions.
	 */
	private void initTransitionsMaps() {
		if (trId2bpnMap == null) {
			trId2bpnMap = new Object2LongOpenHashMap<String>();
			trId2bpnMap.defaultReturnValue(-1L);
		}
		if (tr2InPlacesMap == null) {
			tr2InPlacesMap = new Long2ObjectOpenHashMap<>();
			tr2InPlacesMap.defaultReturnValue(null);
		}
		if (tr2OutPlacesMap == null) {
			tr2OutPlacesMap = new Long2ObjectOpenHashMap<>();
			tr2OutPlacesMap.defaultReturnValue(null);
		}
	}

	/**
	 * Initialize internal data structures for places.
	 */
	private void initPlacesMap() {
		if (placesId2bpnMap == null) {
			placesId2bpnMap = new Object2LongOpenHashMap<String>();
			placesId2bpnMap.defaultReturnValue(-1L);
		}
	}

	private void exportTransitions(AutoPilotHuge ap, VTDNavHuge vn,
			BlockingQueue<String> bpnQueue, BlockingQueue<String> tsQueue)
			throws XPathParseExceptionHuge, NavExceptionHuge,
			InterruptedException, XPathEvalExceptionHuge {
		// count transitions
		ap.selectXPath(PNMLPaths.COUNT_TRANSITIONS_PATH);
		long nb = (long) ap.evalXPathToNumber();
		StringBuilder bpnsb = new StringBuilder();
		bpnsb.append(TRANSITIONS).append(WS).append(HK).append(nb).append(WS)
				.append(ZERO).append(DOTS).append(nb - 1).append(NL);
		bpnQueue.put(bpnsb.toString());
		bpnsb.delete(0, bpnsb.length());
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);

		// Handle transitions through arcs
		String src, trg;
		long count = 0L;
		long tId, pId;
		LongBigArrayBigList pls = null;
		ap.selectXPath(PNMLPaths.ARCS_PATH);
		tsQueue.put(TRANSITIONS_MAPPING_MSG + NL);
		while ((ap.evalXPath()) != -1) {
			pls = null;
			src = vn.toString(vn.getAttrVal(PNMLPaths.SRC_ATTR));
			trg = vn.toString(vn.getAttrVal(PNMLPaths.TRG_ATTR));
			pId = placesId2bpnMap.getLong(src);
			if (pId != -1L) { // transition is the target
				tId = trId2bpnMap.getLong(trg);
				if (tId != -1L) {
					pls = tr2InPlacesMap.get(tId);
				} else {
					tId = count++;
					trId2bpnMap.put(trg, tId);
					tsQueue.put(tId + WS + trg + NL);
				}
				if (pls == null) {
					pls = new LongBigArrayBigList();
					tr2InPlacesMap.put(tId, pls);
				}
			} else {// transition is the source
				pId = placesId2bpnMap.getLong(trg);
				tId = trId2bpnMap.getLong(src);
				if (tId != -1L) {
					pls = tr2OutPlacesMap.get(tId);
				} else {
					tId = count++;
					trId2bpnMap.put(src, tId);
					tsQueue.put(tId + WS + src + NL);
				}
				if (pls == null) {
					pls = new LongBigArrayBigList();
					tr2OutPlacesMap.put(tId, pls);
				}
			}
			pls.add(pId);
		}
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
		LongCollection allTr =new LongRBTreeSet(trId2bpnMap.values());
	
		for (long trId : allTr) {
			bpnsb.append(T).append(trId);
			buildConnectedPlaces2Transition(bpnsb, trId, tr2InPlacesMap);
			buildConnectedPlaces2Transition(bpnsb, trId, tr2OutPlacesMap);
			bpnsb.append(NL);
			bpnQueue.put(bpnsb.toString());
			bpnsb.delete(0, bpnsb.length());
		}
		bpnsb.delete(0, bpnsb.length());
		bpnsb = null;
	}

	/**
	 * @param bpnsb
	 * @param trId
	 */
	private void buildConnectedPlaces2Transition(StringBuilder bpnsb,
			long trId, Long2ObjectOpenHashMap<LongBigArrayBigList> tr2PlacesMap) {
		LongBigArrayBigList pls;
		long plsSize;
		pls = tr2PlacesMap.get(trId);
		if (pls != null) {
			plsSize = pls.size64();
		} else { // no place in input or output list of this transition
			plsSize = 0L;
		}
		bpnsb.append(WS).append(HK).append(plsSize);
		if (plsSize == 0L) { // convention for empty sets
			bpnsb.append(WS).append(ONE).append(DOTS).append(ZERO);
		} else {
			for (long plId : pls) {
				bpnsb.append(WS).append(plId);
			}
		}
	}

	private BlockingQueue<String> initQueue() {
		BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
		return queue;
	}

	private Thread startWriter(OutChannelBean ocb, BlockingQueue<String> queue) {
		Thread t = new Thread(new BPNWriter(ocb, queue));
		t.start();
		return t;
	}

	private void exportPlacesIntoUnits(AutoPilotHuge ap, VTDNavHuge vn,
			BlockingQueue<String> bpnQueue, BlockingQueue<String> tsQueue)
			throws XPathParseExceptionHuge, XPathEvalExceptionHuge,
			NavExceptionHuge, InvalidSafeNetException, InternalException,
			InterruptedException {
		long iDCount = 0L;
		// initial places
		ap.selectXPath(PNMLPaths.PLACES_MARKING);
		String id;
		tsQueue.put(PLACE_MAPPING_MSG + NL);
		List<Long> initPlaces = new ArrayList<>();
		// TODO Check: do we need to clone the vn for using it in the loop?
		// (Case of several initial places...)
		while ((ap.evalXPath()) != -1) {
			vn.toElement(VTDNavHuge.PARENT);
			vn.toElement(VTDNavHuge.PARENT);
			id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));
			if (id != null) {
				try {
					tsQueue.put(iDCount + WS + id + NL);
					placesId2bpnMap.put(id, iDCount);
					initPlaces.add(iDCount);
					iDCount++;
				} catch (InterruptedException e) {
					log.error(e.getMessage());
					throw new InternalException(e.getMessage());

				}
			}
		}
		if (initPlaces.size() == 0) {
			log.warn("Attention: there is no initial place in this net!");
		}
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
		
		// count all places
		ap.selectXPath(PNMLPaths.COUNT_PLACES_PATH);
		long nb = (long) ap.evalXPathToNumber();
		StringBuilder bpnsb = new StringBuilder();
		// Write Number of places
		bpnsb.append(PLACES).append(WS).append(HK).append(nb).append(WS)
				.append(ZERO).append(DOTS).append(nb-1).append(NL);
		// / TODO Handle case where there are several initial places
		if (initPlaces.size() > 1) {
			bpnsb.append(INIT_PLACES).append(WS).append(HK)
					.append(initPlaces.size());
			for (Long l : initPlaces) {
				bpnsb.append(WS).append(l);
			}
		} else {
			bpnsb.append(INIT_PLACE).append(WS).append(ZERO);
		}
		bpnsb.append(NL);
		// Write the number of Units
		bpnsb.append(UNITS).append(WS).append(HK).append(nb + 1).append(WS)
				.append(ZERO).append(DOTS).append(nb).append(NL);

		//TODO No solution yet for the case where there are several initial places
		if (initPlaces.size() > 1) {
			log.error("Attention: there are several initial places and no solution yet for the correct encoding of the resulting BPN.");
			throw new InternalException("No solution yet to export into BPN the case of several initial places");
		}
		// Root unit declaration
		bpnsb.append(ROOT_UNIT).append(WS).append(nb).append(NL);
		bpnQueue.put(bpnsb.toString());
		bpnsb.delete(0, bpnsb.length());

		// One place per unit, keep track of their PNML id in ts file
		// First the initial places
		long count = 0L;
		for (Long l: initPlaces) {
			bpnsb.append(U).append(count).append(WS).append(HK).append(ONE)
			.append(WS).append(l).append(DOTS).append(l)
			.append(WS).append(HK).append(ZERO).append(NL);
			bpnQueue.put(bpnsb.toString());
			count++;
		}
		bpnsb.delete(0, bpnsb.length());
		
		// Then the rest
		ap.selectXPath(PNMLPaths.PLACES_PATH_EXCEPT_MKG);
		StringBuilder tsmapping = new StringBuilder();
		while ((ap.evalXPath()) != -1) {
			
			id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));
			tsmapping.append(iDCount).append(WS).append(id).append(NL);
			tsQueue.put(tsmapping.toString());
			bpnsb.append(U).append(count).append(WS).append(HK).append(ONE)
					.append(WS).append(iDCount).append(DOTS).append(iDCount)
					.append(WS).append(HK).append(ZERO).append(NL);
			bpnQueue.put(bpnsb.toString());
			placesId2bpnMap.put(id, iDCount);
			bpnsb.delete(0, bpnsb.length());
			tsmapping.delete(0, tsmapping.length());
			count++; iDCount++;
		}
		tsmapping = null;
		// / Root Unit N and its subunits
		bpnsb.append(U).append(nb).append(WS).append(HK).append(ZERO)
				.append(WS).append(ONE).append(DOTS).append(ZERO).append(WS)
				.append(HK).append(nb);
		for (count = 0L; count < nb; count++) {
			bpnsb.append(WS).append(count);
		}
		bpnsb.append(NL);
		bpnQueue.put(bpnsb.toString());
		bpnsb.delete(0, bpnsb.length());
		bpnsb = null;
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
	}

	private boolean isPTNet(AutoPilotHuge ap, VTDNavHuge vn)
			throws XPathParseExceptionHuge, XPathEvalExceptionHuge,
			NavExceptionHuge {
		boolean result = true;
		ap.selectXPath(PNMLPaths.NETS_PATH);
		while ((ap.evalXPath()) != -1) {
			String netType = vn.toString(vn.getAttrVal(PNMLPaths.TYPE_ATTR));
			log.info("Discovered net type: {}", netType);
			if (!netType.endsWith(PNMLPaths.PTNET_TYPE)) {
				result = false;
				break;
			}
		}
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
		return result;
	}

	@SuppressWarnings("unused")
	private boolean isNet1Safe(AutoPilotHuge ap, VTDNavHuge vn)
			throws XPathParseExceptionHuge, NavExceptionHuge,
			NumberFormatException, XPathEvalExceptionHuge {
		boolean result = true;
		long count = 0L;
		String mkg;
		ap.selectXPath(PNMLPaths.PLACES_MARKING);
		while ((ap.evalXPath()) != -1) {
			mkg = vn.toString(vn.getText());
			if (Integer.valueOf(mkg) == 1) {
				count++;
			} else {
				break;
			}
		}
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
		if (count != 1L) {
			result = false;
		}
		return result;
	}

	private boolean isNet1Safe() throws IOException, PNMLImportExportException {
		spnc.setPnmlDocPath(this.currentInputFile.getCanonicalPath());
		boolean res = spnc.isNet1Safe();
		return res;
	}
}

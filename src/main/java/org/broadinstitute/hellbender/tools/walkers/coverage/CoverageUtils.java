/*
* Copyright 2012-2016 Broad Institute, Inc.
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.hellbender.tools.walkers.coverage;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import org.broadinstitute.hellbender.engine.AlignmentContext;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.BaseUtils;
import org.broadinstitute.hellbender.utils.pileup.PileupElement;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.ReadUtils;

import java.util.*;

/**
 * IF THERE IS NO JAVADOC RIGHT HERE, YELL AT chartl
 *
 * @Author chartl
 * @Date Mar 3, 2010
 */
public class CoverageUtils {

    public enum CountPileupType {
        /**
         * Count all reads independently (even if from the same fragment).
         */
        COUNT_READS,
        /**
         * Count all fragments (even if the reads that compose the fragment are not consistent at that base).
         */
        COUNT_FRAGMENTS,
        /**
         * Count all fragments (but only if the reads that compose the fragment are consistent at that base).
         */
        COUNT_FRAGMENTS_REQUIRE_SAME_BASE
    }

    /**
     * Returns the counts of bases from reads with MAPQ > minMapQ and base quality > minBaseQ in the context
     * as an array of ints, indexed by the index fields of BaseUtils
     *
     * @param context
     * @param minMapQ
     * @param minBaseQ
     * @return
     */
    public static int[] getBaseCounts(AlignmentContext context, int minMapQ, int minBaseQ) {
        int[] counts = new int[6];

        for (PileupElement e : context.getBasePileup()) {
            if ( e.getMappingQual() >= minMapQ && ( e.getQual() >= minBaseQ || e.isDeletion() ) ) {
                updateCounts(counts,e);
            }
        }

        return counts;
    }

    public static String getTypeID(SAMReadGroupRecord rg, DoCOutputType.Partition type ) {
        switch (type) {
            case sample:
                return rg.getSample();
            case readgroup:
                return rg.getSample()+"_rg_"+rg.getReadGroupId();
            case library:
                return rg.getLibrary();
            case center:
                return rg.getSequencingCenter();
            case platform:
                return rg.getPlatform();
            case sample_by_center:
                return rg.getSample()+"_cn_"+rg.getSequencingCenter();
            case sample_by_platform:
                return rg.getSample()+"_pl_"+rg.getPlatform();
            case sample_by_platform_by_center:
                return rg.getSample()+"_pl_"+rg.getPlatform()+"_cn_"+rg.getSequencingCenter();
            default:
                throw new GATKException(String.format("Invalid aggrigation type %s", type));
        }
    }

    public static Map<DoCOutputType.Partition,Map<String,int[]>>
                    getBaseCountsByPartition(AlignmentContext context, int minMapQ, int maxMapQ, byte minBaseQ, byte maxBaseQ, CountPileupType countType, Collection<DoCOutputType.Partition> types, SAMFileHeader header) {

        Map<DoCOutputType.Partition,Map<String,int[]>> countsByIDByType = new HashMap<DoCOutputType.Partition,Map<String,int[]>>();
        Map<SAMReadGroupRecord,int[]> countsByRG = getBaseCountsByReadGroup(context, minMapQ, maxMapQ, minBaseQ, maxBaseQ, countType, header);
        for (DoCOutputType.Partition t : types ) {
            // iterate through the read group counts and build the type associations
            for ( Map.Entry<SAMReadGroupRecord,int[]> readGroupCountEntry : countsByRG.entrySet() ) {
                String typeID = getTypeID(readGroupCountEntry.getKey(),t);

                if ( ! countsByIDByType.keySet().contains(t) ) {
                    countsByIDByType.put(t,new HashMap<String,int[]>());
                }

                if ( ! countsByIDByType.get(t).keySet().contains(typeID) ) {
                    countsByIDByType.get(t).put(typeID,readGroupCountEntry.getValue().clone());
                } else {
                    addCounts(countsByIDByType.get(t).get(typeID),readGroupCountEntry.getValue());
                }
            }
        }


        return countsByIDByType;
    }

    public static void addCounts(int[] updateMe, int[] leaveMeAlone ) {
        for ( int index = 0; index < leaveMeAlone.length; index++ ) {
            updateMe[index] += leaveMeAlone[index];
        }
    }

    public static Map<SAMReadGroupRecord,int[]> getBaseCountsByReadGroup(AlignmentContext context, int minMapQ, int maxMapQ, byte minBaseQ, byte maxBaseQ, CountPileupType countType, SAMFileHeader header) {
        Map<SAMReadGroupRecord, int[]> countsByRG = new HashMap<SAMReadGroupRecord,int[]>();

        Map<String, int[]> countsByRGName = new HashMap<String, int[]>();
        Map<String, SAMReadGroupRecord> RGByName = new HashMap<String, SAMReadGroupRecord>();

        List<PileupElement> countPileup = new LinkedList<PileupElement>();
//        FragmentCollection<PileupElement> fpile;

        switch (countType) {

            case COUNT_READS:
                for (PileupElement e : context.getBasePileup()) {
                    if (countElement(e, minMapQ, maxMapQ, minBaseQ, maxBaseQ)) {
                        countPileup.add(e);
                    }
                }
                break;

                // TODO see FragmentUtils.create() for details on how the overlapping fragments are discounted
            case COUNT_FRAGMENTS: // ignore base identities and put in FIRST base that passes filters:
                throw new UnsupportedOperationException("This fragment counting is unsupported as of yet");
//                fpile = context.getBasePileup().getStartSortedPileup().toFragments();
//
//                for (PileupElement e : fpile.getSingletonReads())
//                    if (countElement(e, minMapQ, maxMapQ, minBaseQ, maxBaseQ))
//                        countPileup.add(e);
//
//                for (List<PileupElement> overlappingPair : fpile.getOverlappingPairs()) {
//                    // iterate over all elements in fragment:
//                    for (PileupElement e : overlappingPair) {
//                        if (countElement(e, minMapQ, maxMapQ, minBaseQ, maxBaseQ)) {
//                            countPileup.add(e); // add the first passing element per fragment
//                            break;
//                        }
//                    }
//                }
//                break;

            case COUNT_FRAGMENTS_REQUIRE_SAME_BASE:
                throw new UnsupportedOperationException("This fragment counting is unsupported as of yet");
//                fpile = context.getBasePileup().getStartSortedPileup().toFragments();
//
//                for (PileupElement e : fpile.getSingletonReads())
//                    if (countElement(e, minMapQ, maxMapQ, minBaseQ, maxBaseQ))
//                        countPileup.add(e);
//
//                for (List<PileupElement> overlappingPair : fpile.getOverlappingPairs()) {
//                    PileupElement firstElem = null;
//                    PileupElement addElem = null;
//
//                    // iterate over all elements in fragment:
//                    for (PileupElement e : overlappingPair) {
//                        if (firstElem == null)
//                            firstElem = e;
//                        else if (e.getBase() != firstElem.getBase()) {
//                            addElem = null;
//                            break;
//                        }
//
//                        // will add the first passing element per base-consistent fragment:
//                        if (addElem == null && countElement(e, minMapQ, maxMapQ, minBaseQ, maxBaseQ))
//                            addElem = e;
//                    }
//
//                    if (addElem != null)
//                        countPileup.add(addElem);
//                }
//                break;

            default:
                throw new UserException("Must use valid CountPileupType");
        }

        for (PileupElement e : countPileup) {
            SAMReadGroupRecord readGroup = getReadGroup(e.getRead(), header);

            // uniqueReadGroupID is unique across the library, read group ID, and the sample
            String uniqueReadGroupId = readGroup.getSample() + "_" + readGroup.getReadGroupId() + "_" + readGroup.getLibrary() + "_" + readGroup.getPlatformUnit();
            int[] counts = countsByRGName.get(uniqueReadGroupId);
            if (counts == null) {
                counts = new int[6];
                countsByRGName.put(uniqueReadGroupId, counts);
                RGByName.put(uniqueReadGroupId, readGroup);
            }

            updateCounts(counts, e);
        }

        for (String readGroupId : RGByName.keySet()) {
            countsByRG.put(RGByName.get(readGroupId), countsByRGName.get(readGroupId));
        }

        return countsByRG;
    }

    private static boolean countElement(PileupElement e, int minMapQ, int maxMapQ, byte minBaseQ, byte maxBaseQ) {
        return (e.getMappingQual() >= minMapQ && e.getMappingQual() <= maxMapQ && ( e.getQual() >= minBaseQ && e.getQual() <= maxBaseQ || e.isDeletion() ));
    }

    private static void updateCounts(int[] counts, PileupElement e) {
        if ( e.isDeletion() ) {
            counts[BaseUtils.Base.D.ordinal()]++;
        } else if ( BaseUtils.basesAreEqual(BaseUtils.Base.N.base, e.getBase()) ) {
            counts[BaseUtils.Base.N.ordinal()]++;
        } else {
            try {
                counts[BaseUtils.simpleBaseToBaseIndex(e.getBase())]++;
            } catch (ArrayIndexOutOfBoundsException exc) {
                throw new GATKException("Expected a simple base, but actually received"+(char)e.getBase());
            }
        }
    }

    private static SAMReadGroupRecord getReadGroup(final GATKRead r, SAMFileHeader header) {
        SAMReadGroupRecord rg = ReadUtils.getSAMReadGroupRecord(r, header);
        if ( rg == null ) {
            String msg = "Read "+r.getName()+" lacks read group information; Please associate all reads with read groups";
            throw new UserException.MalformedBAM(r, msg);
        }

        return rg;
    }

    /*
     * @updateTargetTable
     * The idea is to have counts for how many *targets* have at least K samples with
     * median coverage of at least X.
     * To that end:
     * Iterate over the samples the DOCS object, determine how many there are with
     * median coverage > leftEnds[0]; how many with median coverage > leftEnds[1]
     * and so on. Then this target has at least N, N-1, N-2, ... 1, 0 samples covered
     * to leftEnds[0] and at least M,M-1,M-2,...1,0 samples covered to leftEnds[1]
     * and so on.
     */
    public static void updateTargetTable(int[][] table, DepthOfCoverageStats stats) {
        int[] cutoffs = stats.getEndpoints();
        int[] countsOfMediansAboveCutoffs = new int[cutoffs.length+1]; // 0 bin to catch everything
//        for ( int i = 0; i < countsOfMediansAboveCutoffs.length; i ++) {
//            countsOfMediansAboveCutoffs[i]=0;
//        } //TODO why in the heck was this necessary... this

        for ( String s : stats.getAllSamples() ) {
            int medianBin = getQuantile(stats.getHistograms().get(s),0.5);
            for ( int i = 0; i <= medianBin; i ++) {
                countsOfMediansAboveCutoffs[i]++;
            }
        }

        for ( int medianBin = 0; medianBin < countsOfMediansAboveCutoffs.length; medianBin++) {
            for ( ; countsOfMediansAboveCutoffs[medianBin] > 0; countsOfMediansAboveCutoffs[medianBin]-- ) {
                table[countsOfMediansAboveCutoffs[medianBin]-1][medianBin]++;
                // the -1 is due to counts being 1-based and offsets being 0-based
            }
        }
    }

    //TODO commentme
    public static double getPctBasesAbove(long[] histogram, int bin) {
        long below = 0l;
        long above = 0l;
        for ( int index = 0; index < histogram.length; index++) {
            if ( index < bin ) {
                below+=histogram[index];
            } else {
                above+=histogram[index];
            }
        }

        return 100*( (double) above )/( above + below );
    }

    //TODO commentme
    public static int getQuantile(long[] histogram, double prop) {
        int total = 0;

        for ( int i = 0; i < histogram.length; i ++ ) {
            total += histogram[i];
        }

        int counts = 0;
        int bin = -1;
        while ( counts < prop*total ) {
            counts += histogram[bin+1];
            bin++;
        }

        return bin == -1 ? 0 : bin;
    }

}

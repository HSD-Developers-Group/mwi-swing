package eu.kprod.gui;

import java.awt.Color;
import java.text.SimpleDateFormat;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;

public final class MwChartFactory {

    private MwChartFactory() {

    }
    
    public static MwChartPanel createChart(final XYDataset xyDataset) {
        final JFreeChart chart;
        // final XYDataset dataset
       
        chart = ChartFactory.createTimeSeriesChart(null, null,
                    null, xyDataset, true, true, false);
        

        chart.setBackgroundPaint(Color.white);
        
        final XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        
        
        final DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("mm''ss''''SSS"));

        // force integer display
        ValueAxis va = (ValueAxis) plot.getRangeAxis();
        va.setStandardTickUnits(NumberAxis.createIntegerTickUnits()); 
        MwChartPanel chartPanel = new MwChartPanel(chart);
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
    
        return chartPanel;

    }

}

package com.ecommint.accounthr.dto.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel ay-sheet import işleminin özet raporu (E2-01).
 *
 * <p>Sheet başına kırılım ({@link SheetSummary}) + tüm sheet'ler için büyük toplamlar.
 * Tüm sayaçlar satır temellidir; para alanı içermez.
 */
public class ImportSummary {

    private List<SheetSummary> sheets = new ArrayList<>();

    private int totalRowsRead;
    private int totalImported;
    private int totalSkippedEmpty;
    private int totalSkippedTotal;
    private int totalSkippedDuplicate;

    public List<SheetSummary> getSheets() {
        return sheets;
    }

    public void setSheets(List<SheetSummary> sheets) {
        this.sheets = sheets;
    }

    /** Bir sheet özetini ekler ve büyük toplamları günceller. */
    public void addSheet(SheetSummary sheet) {
        sheets.add(sheet);
        totalRowsRead += sheet.getRowsRead();
        totalImported += sheet.getImported();
        totalSkippedEmpty += sheet.getSkippedEmpty();
        totalSkippedTotal += sheet.getSkippedTotal();
        totalSkippedDuplicate += sheet.getSkippedDuplicate();
    }

    public int getTotalRowsRead() {
        return totalRowsRead;
    }

    public int getTotalImported() {
        return totalImported;
    }

    public int getTotalSkippedEmpty() {
        return totalSkippedEmpty;
    }

    public int getTotalSkippedTotal() {
        return totalSkippedTotal;
    }

    public int getTotalSkippedDuplicate() {
        return totalSkippedDuplicate;
    }

    /** Tek bir ay sheet'inin import kırılımı. */
    public static class SheetSummary {

        private String sheetName;
        private String periodCode;
        private int rowsRead;
        private int imported;
        private int skippedEmpty;
        private int skippedTotal;
        private int skippedDuplicate;

        public SheetSummary() {
        }

        public SheetSummary(String sheetName, String periodCode) {
            this.sheetName = sheetName;
            this.periodCode = periodCode;
        }

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public String getPeriodCode() {
            return periodCode;
        }

        public void setPeriodCode(String periodCode) {
            this.periodCode = periodCode;
        }

        public int getRowsRead() {
            return rowsRead;
        }

        public void setRowsRead(int rowsRead) {
            this.rowsRead = rowsRead;
        }

        public void incrementRowsRead() {
            this.rowsRead++;
        }

        public int getImported() {
            return imported;
        }

        public void incrementImported() {
            this.imported++;
        }

        public int getSkippedEmpty() {
            return skippedEmpty;
        }

        public void incrementSkippedEmpty() {
            this.skippedEmpty++;
        }

        public int getSkippedTotal() {
            return skippedTotal;
        }

        public void incrementSkippedTotal() {
            this.skippedTotal++;
        }

        public int getSkippedDuplicate() {
            return skippedDuplicate;
        }

        public void incrementSkippedDuplicate() {
            this.skippedDuplicate++;
        }
    }
}

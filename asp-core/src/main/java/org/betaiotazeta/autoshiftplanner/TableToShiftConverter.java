package org.betaiotazeta.autoshiftplanner;

import java.util.List;

/**
 * Converts the worked cells of a {@link Table} into assigned {@link ShiftAssignment}s on a
 * {@link Solution}: it scans each row for maximal runs of worked cells and, for every run, sets the
 * matching {@link TimeGrain} (start) and {@link ShiftDuration} (length) on a free shift assignment of
 * the corresponding employee.
 *
 * <p>This is pure problem-construction logic with no UI dependency (extracted from the former
 * {@code AspApp.convertTableIntoShifts()} so the future backend can reuse it). When a run cannot be
 * mapped to the current constraint settings it throws {@link ShiftConversionException} instead of
 * showing a dialog; the caller decides how to surface the message.
 */
public class TableToShiftConverter {

    public void convert(Table table, Solution solution) throws ShiftConversionException {
        int nR = table.getNumberOfRows();
        int nC = table.getnumberOfColumns();

        List<ShiftAssignment> shiftAssignmentList = solution.getShiftAssignmentList();
        List<ShiftDuration> shiftDurationList = solution.getShiftDurationList();

        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nC; j++) {
                ShiftDuration shiftDuration;
                boolean status = false;
                int shiftStart = -1;
                status = table.getCell(i, j).isWorked();
                while (status) {
                    if (shiftStart == -1) {
                        shiftStart = j;
                    }
                    j = j + 1;
                    if (j < nC) {
                        status = table.getCell(i, j).isWorked();
                    } else {
                        status = false;
                    }
                }
                if (shiftStart == -1) {
                    // do nothing
                } else {
                    // convert into shift
                    //  idEployee needs to be corrected because indexOfEmployee starts at 0: -1
                    int idEmployee = table.getCell(i, shiftStart).getIdEmployee() - 1;
                    //  periods and timeGrains are essentially the same thing
                    //  idPeriod needs to be corrected because grainIndex starts at 0: -1
                    int grainIndex = table.getCell(i, shiftStart).getIdPeriod() - 1;
                    int durationInGrains = (j - shiftStart); // column j means first non worked cell

                    boolean done = false;
                    int index = 0;
                    int maxIndex = shiftDurationList.size() - 1;
                    do {
                        shiftDuration = shiftDurationList.get(index);
                        if (durationInGrains == shiftDuration.getDurationInGrains()) {
                            done = true;
                            index = 0;
                        }
                        index++;
                        if (maxIndex < index) {
                            String message = "A shift has a length of: " + durationInGrains + " grains, which is uncompatible with constraints settings.";
                            throw new ShiftConversionException(message);
                        }
                    } while (!done);

                    done = false;
                    index = 0;
                    maxIndex = shiftAssignmentList.size() - 1;
                    do {
                        ShiftAssignment shiftAssignment = shiftAssignmentList.get(index);
                        int indexOfEmployee = solution.getStaffScore().indexOf(shiftAssignment.getShift().getEmployee());
                        if ((idEmployee == indexOfEmployee)
                                && ((shiftAssignment.getTimeGrain() == null) || (shiftAssignment.getShiftDuration() == null))) {
                            // assigning...
                            shiftAssignment.setTimeGrain(solution.getTimeGrainList().get(grainIndex));
                            shiftAssignment.setShiftDuration(shiftDuration);
                            done = true;
                            index = 0;
                        }
                        index++;
                        if (maxIndex < index) {
                            String employeeName = solution.getStaffScore().get(idEmployee).getName();
                            String message = "Cannot assign a shift! " + "Employee: " + employeeName + ", grainIndex: " + grainIndex + ", duration:" + durationInGrains + ". Please, check settings.";
                            throw new ShiftConversionException(message);
                        }
                    } while (!done);
                }
            }
        }
    }
}

package org.betaiotazeta.autoshiftplanner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;

/**
 *
 * @author betaiotazeta
 */
public class AspEasyScoreCalculator implements EasyScoreCalculator<Solution, HardSoftScore> {

    @Override
    public HardSoftScore calculateScore(Solution solution) {
        
        // Values are in half-hour units of time
        Configurator configurator = solution.getConfigurator();
        int shiftsPerDay = (configurator.getShiftsPerDay() * 2);
        int breakLenght = (int) (configurator.getBreakLenght() * 2);
        int shiftLenghtMin = (int) (configurator.getShiftLenghtMin() * 2);
        int shiftLenghtMax = (int) (configurator.getShiftLenghtMax() * 2);
        int employeesPerPeriod = configurator.getEmployeesPerPeriod();
        int hoursPerDay = (int) (configurator.getHoursPerDay() * 2);
        int overnightRest = (int) (configurator.getOvernightRest() * 60);
        
        Table tableScore = solution.getTableScore();
        int nR = tableScore.getNumberOfRows();
        int nC = tableScore.getnumberOfColumns();
        List<Employee> staffScore = solution.getStaffScore();
        List<ShiftAssignment> shiftAssignmentList = solution.getShiftAssignmentList();
        int numberOfEmployees = staffScore.size();
        Employee employee;
                
        int hardScore = 0;
        int softScore = 0;
        
        // reset the tableScore
        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nC; j++) {
                tableScore.getCell(i, j).setWorked(false);
            }
        }
        
        // convert shifts into tableScore
        for (ShiftAssignment shiftAssignment : shiftAssignmentList) {
            if ((shiftAssignment.getTimeGrain() != null) && (shiftAssignment.getShiftDuration() != null)) {
                int startingGrainOfDay = shiftAssignment.getTimeGrain().getStartingGrainOfDay();
                int dayOfWeek = shiftAssignment.getTimeGrain().getDay().getDayOfWeek();
                int durationInGrains = shiftAssignment.getShiftDuration().getDurationInGrains();
                int indexOfEmployee = staffScore.indexOf(shiftAssignment.getShift().getEmployee());
                int i = indexOfEmployee + (dayOfWeek * numberOfEmployees);
                int finalGrainOfDay = startingGrainOfDay + durationInGrains;
                if (finalGrainOfDay >= nC) {
                    int overflow = finalGrainOfDay - nC;
                    finalGrainOfDay = finalGrainOfDay - overflow;
                    hardScore = hardScore - overflow;
                }
                for (int j = startingGrainOfDay; j < finalGrainOfDay; j++) {
                    /*
                    if (tableScore.getCell(i, j).isWorked()) {
                        // allow shifts to overlap may help during solving!
                        hardScore = hardScore - 1; // overlapping shifts
                    }
                    */
                    tableScore.getCell(i, j).setWorked(true);
                }
            }
        }
                
        // reset of all hours performed for everyone
        for (int i = 0; i < numberOfEmployees; i++) {
            employee = staffScore.get(i);
            employee.setHoursWorked(0);
        }

        // update the hours worked for all employees from the data in the tableScore
        // attention: values of nR and nC start at 0
        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nC; j++) {
                boolean status = tableScore.getCell(i, j).isWorked();
                if (status) {
                    byte idDip = tableScore.getCell(i, j).getIdEmployee();
                    // IdDip count starts at 1.
                    // Position in arrayList starts at 0. Subtracting 1.
                    employee = staffScore.get(idDip - 1);
                    employee.setHoursWorked(employee.getHoursWorked() + 0.5);
                }
            }
        }        
        
        /* ----- SCORE ----- */
        
        // score for all employees to do the required number of hours per week      
        if (configurator.isHoursPerWeekCheck()) {
            for (int i = 0; i < numberOfEmployees; i++) {
                employee = staffScore.get(i);
                int halfHoursWorked = (int) (employee.getHoursWorked() * 2);
                int halfHoursPerWeek = employee.getHoursPerWeek() * 2;
                hardScore -= Math.abs(halfHoursPerWeek - halfHoursWorked);
            }
        }
         
        // score so that each employee does not exceed the number of shifts per day
        if (configurator.isShiftsPerDayCheck()) {
            for (int i = 0; i < nR; i++) {
                int k = 0;
                for (int j = 1; j < nC; j++) {
                    boolean status1 = tableScore.getCell(i, j - 1).isWorked();
                    if ((j == 1) && (status1)) {
                        k++;
                    }
                    boolean status2 = tableScore.getCell(i, j).isWorked();
                    if (status1 != status2) {
                        k++;
                    }
                    if ((j == nC - 1) && (k > shiftsPerDay)) {
                        hardScore -= k;
                        k = 0;
                    }
                }
            }         
        }

        // score so that every employee has some eventual breaks of the desired break duration
        // k indicates the lenght of the break in half-hour units
        if (configurator.isBreakLenghtCheck()) {
            for (int i = 0; i < nR; i++) {
                boolean status = false;
                int k = 0;
                for (int j = 0; j < nC; j++) {
                    if (tableScore.getCell(i, j).isWorked()) {
                        if (k > 0 && k != breakLenght) {
                            hardScore -= Math.abs(k - breakLenght);
                        }
                        status = true;
                        k = 0;
                    } else if (status) {
                        k++;
                    }
                }
            }
            
        }
        
        // score for each employee to work the required number of consecutive hours
        if (configurator.isShiftLenghtCheck()) {                 
            for (int i = 0; i < nR; i++) {
                for (int j = 0; j < nC; j++) {
                    int k = 0;
                    while (tableScore.getCell(i, j).isWorked()) {
                        k++;
                        j++;
                        if (j >= nC) {
                            break;
                        }
                    }
                    if (k == 0) {
                        // do nothing
                    } else if (k < shiftLenghtMin) {
                        hardScore -= Math.abs(k - shiftLenghtMin);
                    } else if (k > shiftLenghtMax) {
                        hardScore -= (k - shiftLenghtMax);
                    }
                }
            }      
        }

        // score so that there is the required number of employees per period
        if (configurator.isEmployeesPerPeriodCheck()) {    
            for (int j = 0; j < nC; j++) {
                int k = 0;
                for (int i = 0; i < nR; i += numberOfEmployees) {
                    for (int m = 0; m < numberOfEmployees; m++) {
                        k += tableScore.getCell(i + m, j).isWorked() ? 1 : 0;
                    }
                    if (k < employeesPerPeriod) {
                        hardScore -= Math.abs(k - employeesPerPeriod);
                    }
                    k = 0;
                }
            } 
        }
        
        // score so that each employee does not exceed the expected daily hours
        if (configurator.isHoursPerDayCheck()) {
            int k = 0;
            for (int i = 0; i < nR; i++) {
                k = 0;
                for (int j = 0; j < nC; j++) {
                    k += tableScore.getCell(i, j).isWorked() ? 1 : 0;
                }
                if (k > hoursPerDay) {
                    hardScore -= (k - hoursPerDay) * 2;
                }
            }
            
        }

        // score for all the mandatory cells to be worked
        if (configurator.isMandatoryShiftsCheck()) {
            for (int i = 0; i < nR; i++) {
                for (int j = 0; j < nC; j++) {
                    Cell cell = tableScore.getCell(i, j);
                    if (cell.isMandatory() && !cell.isWorked()) {
                        hardScore -= 10;
                    }
                }
            }
        }

        // score for all the forbidden cells not to be worked
        // This will be run anyway because a cell in gui cannot be forbidden and
        // also worked or mandatory at the same time.
        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nC; j++) {
                Cell cell = tableScore.getCell(i, j);
                if (cell.isForbidden() && cell.isWorked()) {
                    hardScore -= 10;
                }
            }
        }

        // score for minimum amount of overnight rest
        if (configurator.isOvernightRestCheck()) {
            // Check the overnight rest for each employee
            for (int indexOfEmployee = 0; indexOfEmployee < numberOfEmployees; indexOfEmployee++) {
                int firstWorkedCellMinute = 0;
                int lastWorkedCellMinute = 0;
                for (int i = indexOfEmployee; i < nR; i += numberOfEmployees) {
                    boolean flag = false;
                    // Find the first worked cell for this employee on this day
                    for (int j = 0; j < nC; j++) {
                        Cell cell = tableScore.getCell(i, j);
                        if (cell.isWorked()) {
                            firstWorkedCellMinute = cell.getStartingMinuteOfDay();
                            flag = true;
                            break;
                        }
                    }
                    if (flag) {
                        // Calculate the rest period between the end of the previous shift and the start
                        // of the current shift
                        int calculatedRest = (1440 - lastWorkedCellMinute) + firstWorkedCellMinute;
                        if (calculatedRest < overnightRest) {
                            // Penalize the score if the rest period is too short
                            hardScore -= (overnightRest - calculatedRest) / 30;
                        }
                        // Find the last worked cell for this employee on this day
                        for (int j = nC - 1; j >= 0; j--) {
                            Cell cell = tableScore.getCell(i, j);
                            if (cell.isWorked()) {
                                lastWorkedCellMinute = cell.getStartingMinuteOfDay() + 30;
                                break;
                            }
                        }
                    } else {
                        lastWorkedCellMinute = 0;
                    }
                }
            }
        }
        
        // score so that employees are evenly distributed
        if (configurator.isUniformEmployeesDistributionCheck()) {
            // Keep track of how many employees are working in each period
            Map<Short, Byte> periodUsageMap = new HashMap<>();
            for (int i = 0; i < nR; i++) {
                for (int j = 0; j < nC; j++) {
                    if (tableScore.getCell(i, j).isWorked()) {
                        short period = tableScore.getCell(i, j).getIdPeriod();
                        periodUsageMap.put(period, (byte) (periodUsageMap.getOrDefault(period, (byte) 0) + 1));
                    }
                }
            }

            // Subtract the square of the number of employees working in each period from
            // the score
            for (byte usage : periodUsageMap.values()) {
                softScore -= usage * usage;
            }
        }
        

        return HardSoftScore.of(hardScore, softScore);
    }
}

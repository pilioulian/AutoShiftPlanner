package org.betaiotazeta.autoshiftplanner;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 *
 * @author betaiotazeta
 */
@PlanningEntity
public class ShiftAssignment {

    /**
     * Stable unique id, required by Constraint Streams pair/exists joins. There is a 1:1 mapping
     * between a ShiftAssignment and its Shift, so the shift's unique index serves as the id.
     */
    @PlanningId
    public int getId() {
        return shift.getShiftIndex();
    }

    public Shift getShift() {
        return shift;
    }

    public void setShift(Shift shift) {
        this.shift = shift;
    }

    @PlanningVariable(valueRangeProviderRefs = {"timeGrainRange"}, nullable = true)
    public TimeGrain getTimeGrain() {
        return timeGrain;
    }

    public void setTimeGrain(TimeGrain timeGrain) {
        this.timeGrain = timeGrain;
    }

    @PlanningVariable(valueRangeProviderRefs = {"shiftDurationRange"}, nullable = true)
    public ShiftDuration getShiftDuration() {
        return shiftDuration;
    }

    public void setShiftDuration(ShiftDuration shiftDuration) {
        this.shiftDuration = shiftDuration;
    }

    @Override
    public String toString() {
        return "s" + shift.getShiftIndex() + "(" + shift.getEmployee().getName() + ")";
    }
    
    private Shift shift;
    private TimeGrain timeGrain;
    private ShiftDuration shiftDuration;   
}
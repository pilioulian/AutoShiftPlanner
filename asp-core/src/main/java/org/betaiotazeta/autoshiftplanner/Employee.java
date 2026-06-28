package org.betaiotazeta.autoshiftplanner;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 *
 * @author betaiotazeta
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Employee implements Cloneable {

    /** For Jackson field-based deserialization (see JsonSolutionFileIO). */
    private Employee() {
    }

    public Employee(String name, int hoursPerWeek) {
        this.name = name;
        this.hoursPerWeek = hoursPerWeek;
        hoursWorked = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(int hoursPerWeek) {
        this.hoursPerWeek = hoursPerWeek;
    }

    public double getHoursWorked() {
        return hoursWorked;
    }

    public void setHoursWorked(double hoursWorked) {
        this.hoursWorked = hoursWorked;
    }

    @Override
    protected Employee clone() {
        try {
            return (Employee) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String toString() {
        return "Employee{" + "name=" + name + ", hPW=" + hoursPerWeek + ", hW=" + hoursWorked + "}";
    }

    private String name;
    private int hoursPerWeek;
    private double hoursWorked;
}
/* Soot - a Java Optimization Framework
 * Copyright (C) 2012 Michael Markert, Frank Hartmann
 * 
 * (c) 2012 University of Luxembourg - Interdisciplinary Centre for
 * Security Reliability and Trust (SnT) - All rights reserved
 * Alexandre Bartel
 * 
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.dexpler.instructions;

import java.util.HashSet;
import java.util.Set;

import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;

import soot.Local;
import soot.Type;
import soot.dexpler.DexBody;
import soot.dexpler.DexType;
import soot.dexpler.IDalvikTyper;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.Jimple;
import soot.jimple.internal.JAssignStmt;

public class CheckCastInstruction extends DexlibAbstractInstruction {

    AssignStmt assign = null;
  
    public CheckCastInstruction (Instruction instruction, int codeAdress) {
        super(instruction, codeAdress);
    }

    public void jimplify (DexBody body) {
        if(!(instruction instanceof Instruction21c))
            throw new IllegalArgumentException("Expected Instruction21c but got: "+instruction.getClass());

        Instruction21c checkCastInstr = (Instruction21c)instruction;

        Local castValue = body.getRegisterLocal(checkCastInstr.getRegisterA());
        Type checkCastType = DexType.toSoot((TypeReference) checkCastInstr.getReference());

        CastExpr castExpr =  Jimple.v().newCastExpr(castValue, checkCastType);

        //generate "x = (Type) x"
        //splitter will take care of the rest
        assign = Jimple.v().newAssignStmt(castValue, castExpr);

        setUnit(assign);
        tagWithLineNumber(assign);
        body.add(assign);
        
		}
		public void getConstraint(IDalvikTyper dalvikTyper) {
				if (IDalvikTyper.ENABLE_DVKTYPER) {
          int op = (int)instruction.getOpcode().value;
          dalvikTyper.captureAssign((JAssignStmt)assign, op); // TODO: type could be null!
        }
    }

    @Override
    public Set<DexType> introducedTypes() {
        ReferenceInstruction i = (ReferenceInstruction) instruction;

        Set<DexType> types = new HashSet<DexType>();
        types.add(new DexType((TypeReference) i.getReference()));
        return types;
    }
}

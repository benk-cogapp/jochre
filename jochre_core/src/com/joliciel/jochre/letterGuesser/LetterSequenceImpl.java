///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2012 Assaf Urieli
//
//This file is part of Jochre.
//
//Jochre is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Jochre is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Jochre.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.jochre.letterGuesser;

import java.util.ArrayList;

import com.joliciel.jochre.boundaries.BoundaryService;
import com.joliciel.jochre.boundaries.ShapeInSequence;
import com.joliciel.jochre.boundaries.ShapeSequence;
import com.joliciel.jochre.graphics.Shape;
import com.joliciel.talismane.utils.util.WeightedOutcome;

/**
 * 
 * @author Assaf Urieli
 *
 */
final class LetterSequenceImpl extends ArrayList<WeightedOutcome<String>> implements Comparable<LetterSequenceImpl>, LetterSequence {
	private static final long serialVersionUID = 7846011721688179506L;

	private double score = 0;
	private double adjustedScore = 0;
	private boolean scoreCalculated = false;
	private String string = null;
	private String realWord = null;
	private String guessedWord = null;
	private String realSequence = null;
	private String guessedSequence = null;
	
	private int dashToSkip = -1;
	private ShapeSequence underlyingShapeSequence;
	private BoundaryService boundaryService;
	private int frequency = 0;
	
	public LetterSequenceImpl(ShapeSequence underlyingShapeSequence) {
		super();
		this.underlyingShapeSequence = underlyingShapeSequence;
	}
	
	public LetterSequenceImpl(ShapeSequence underlyingShapeSequence, int initialCapacity) {
		super(initialCapacity);
		this.underlyingShapeSequence = underlyingShapeSequence;
	}
	
	/**
	 * Create a letter sequence with space to one additional letter at the end
	 * of an existing history.
	 * @param history
	 */
	public LetterSequenceImpl(LetterSequence history) {
		super(history.size()+1);
		this.addAll(history);
		this.underlyingShapeSequence = history.getUnderlyingShapeSequence();
	}
	
	/**
	 * Combine two sequences into one.
	 * @param sequence1
	 * @param sequence2
	 */
	public LetterSequenceImpl(LetterSequence sequence1, LetterSequence sequence2, BoundaryService boundaryService) {
		super(sequence1.size() + sequence2.size());
		this.addAll(sequence1);
		this.addAll(sequence2);
		this.setDashToSkip(sequence1.getDashToSkip());
		this.boundaryService = boundaryService;
		
		this.underlyingShapeSequence = this.boundaryService.getShapeSequence(sequence1.getUnderlyingShapeSequence(), sequence2.getUnderlyingShapeSequence());
	}
	
	/* (non-Javadoc)
	 * @see com.joliciel.jochre.training.LetterSequence#getScore()
	 */
	@Override
	public double getScore() {
		if (!scoreCalculated) {
			for (WeightedOutcome<String> outcome : this) {
				score = score + outcome.getWeightLog();
			}
			score = score / this.size();
			// instead of adding the logs, doing a geometric mean
			score = Math.exp(score);
			
			if (this.underlyingShapeSequence!=null)
				score = score * this.underlyingShapeSequence.getScore();
			
			scoreCalculated = true;
		}
		return score;
	}
	
	public void setScore(double score) {
		this.score = score;
		scoreCalculated = true;
	}

	@Override
	public int compareTo(LetterSequenceImpl o) {
		if (this.equals(o))
			return 0;
		if (this.getScore()<o.getScore()) {
			return 1;
		} else if (this.getScore()>o.getScore()) {
			return -1;
		} else {
			return 1;
		}
	}

	@Override
	public synchronized String toString() {
		if (string==null) {
			string = "Sequence: " + this.getGuessedSequence();
		}
		return string;
	}
	

	@Override
	public String getRealWord() {
		if (realWord==null) {
			realWord = this.getRealSequence();
			
			realWord = realWord.replace("[]", "");
			
			// split letters are joined back together
			realWord = realWord.replaceAll("\\|(.){2}\\|", "$1");
			realWord = realWord.replaceAll("\\|(..){2}\\|", "$1");
			realWord = realWord.replaceAll("\\|(...){2}\\|", "$1");

		}
		return realWord;
	}
	

	@Override
	public String getGuessedWord() {
		if (guessedWord==null) {
			guessedWord = this.getGuessedSequence();
			
			guessedWord = guessedWord.replace("[]", "");
			
			// split letters are joined back together
			guessedWord = guessedWord.replaceAll("\\|(.){2}\\|", "$1");
			guessedWord = guessedWord.replaceAll("\\|(..){2}\\|", "$1");
			guessedWord = guessedWord.replaceAll("\\|(...){2}\\|", "$1");

		}
		return guessedWord;
	}

	@Override
	public String getRealSequence() {
		if (realSequence==null) {
			StringBuilder realWordBuilder = new StringBuilder();
			Shape lastShape = null;
			for (ShapeInSequence shapeInSequence : this.getUnderlyingShapeSequence()) {
				for (Shape originalShape : shapeInSequence.getOriginalShapes()) {
					if (!originalShape.equals(lastShape)) {
						String letter = originalShape.getLetter();
						if (letter.length()==0)
							realWordBuilder.append("[]");
						else
							realWordBuilder.append(letter);
					}
					lastShape = originalShape;
				}
			}
			realSequence = realWordBuilder.toString();
		}
		return realSequence;
	}

	@Override
	public String getGuessedSequence() {
		if (guessedSequence==null) {
			StringBuilder builder = new StringBuilder();
			for (WeightedOutcome<String> outcome : this) {
				String letter = outcome.getOutcome();
				if (letter.length()==0)
					builder.append("[]");
				else
					builder.append(letter);
			}
			guessedSequence = builder.toString();
		}
		return guessedSequence;
	}

	public int getDashToSkip() {
		return dashToSkip;
	}

	public void setDashToSkip(int dashToSkip) {
		this.dashToSkip = dashToSkip;
	}

	@Override
	public double getAdjustedScore() {
		return adjustedScore;
	}

	@Override
	public void setAdjustedScore(double adjustedScore) {
		this.adjustedScore = adjustedScore;
	}

	public ShapeSequence getUnderlyingShapeSequence() {
		return underlyingShapeSequence;
	}

	public void setUnderlyingShapeSequence(ShapeSequence underlyingShapeSequence) {
		this.underlyingShapeSequence = underlyingShapeSequence;
	}

	@Override
	public ShapeInSequence getNextShape() {
		if (this.underlyingShapeSequence.size()<=this.size())
			return null;
		else
			return this.underlyingShapeSequence.get(this.size());
	}

	public BoundaryService getBoundaryService() {
		return boundaryService;
	}

	public void setBoundaryService(BoundaryService boundaryService) {
		this.boundaryService = boundaryService;
	}

	public int getFrequency() {
		return frequency;
	}

	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}


}

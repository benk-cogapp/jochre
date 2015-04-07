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
import java.util.List;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.jochre.boundaries.ShapeInSequence;
import com.joliciel.jochre.graphics.Shape;
import com.joliciel.jochre.letterGuesser.features.LetterFeature;
import com.joliciel.talismane.machineLearning.Decision;
import com.joliciel.talismane.machineLearning.DecisionMaker;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.FeatureService;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.utils.PerformanceMonitor;

final class LetterGuesserImpl implements LetterGuesser {
	private static final Log LOG = LogFactory.getLog(LetterGuesserImpl.class);
	private static final PerformanceMonitor MONITOR = PerformanceMonitor.getMonitor(LetterGuesserImpl.class);
	
	private static final double MIN_PROB_TO_STORE = 0.001;
	
	DecisionMaker decisionMaker = null;
	Set<LetterFeature<?>> features = null;
	
	LetterGuesserServiceInternal letterGuesserServiceInternal;
	FeatureService featureService;
	
	public LetterGuesserImpl(Set<LetterFeature<?>> features, DecisionMaker decisionMaker) {
		this.decisionMaker = decisionMaker;
		this.features = features;
	}
	
	public String guessLetter(ShapeInSequence shapeInSequence) {
		return this.guessLetter(shapeInSequence, null);
	}


	@Override
	public String guessLetter(ShapeInSequence shapeInSequence, LetterSequence history) {
		MONITOR.startTask("guessLetter");
		try {
			Shape shape = shapeInSequence.getShape();
			if (LOG.isTraceEnabled())
				LOG.trace("guessLetter, shape: " + shape);
			
			
			List<FeatureResult<?>> featureResults = new ArrayList<FeatureResult<?>>();
			
			MONITOR.startTask("analyse features");
			try {
				for (LetterFeature<?> feature : features) {
					MONITOR.startTask(feature.getName());
					try {
						LetterGuesserContext context = this.letterGuesserServiceInternal.getContext(shapeInSequence, history);
						RuntimeEnvironment env = this.featureService.getRuntimeEnvironment();
						FeatureResult<?> featureResult = feature.check(context, env);
						if (featureResult!=null) {
							featureResults.add(featureResult);
							if (LOG.isTraceEnabled()) {
								LOG.trace(featureResult.toString());
							}
						}
					} finally {
						MONITOR.endTask();
					}
				}
			} finally {
				MONITOR.endTask();
			}
			
			List<Decision> letterGuesses = null;
			MONITOR.startTask("decision maker");
			try {
				letterGuesses = decisionMaker.decide(featureResults);
			} finally {
				MONITOR.endTask();
			}
			
			String bestOutcome = null;
			MONITOR.startTask("store outcomes");
			try {
				shape.getLetterGuesses().clear();
		
				for (Decision letterGuess : letterGuesses) {
					if (letterGuess.getProbability()>=MIN_PROB_TO_STORE) {
						shape.getLetterGuesses().add(letterGuess);
					}
				}
				
				bestOutcome = shape.getLetterGuesses().iterator().next().getOutcome();
			} finally {
				MONITOR.endTask();
			}
			
			if (LOG.isTraceEnabled()) {
				LOG.trace("Shape: " + shape);
				LOG.trace("Letter: " + shape.getLetter());
				LOG.trace("Best outcome: " + bestOutcome);
			}

			return bestOutcome;
		} finally {
			MONITOR.endTask();
		}
	}

	public LetterGuesserServiceInternal getLetterGuesserServiceInternal() {
		return letterGuesserServiceInternal;
	}

	public void setLetterGuesserServiceInternal(
			LetterGuesserServiceInternal letterGuesserServiceInternal) {
		this.letterGuesserServiceInternal = letterGuesserServiceInternal;
	}

	public FeatureService getFeatureService() {
		return featureService;
	}

	public void setFeatureService(FeatureService featureService) {
		this.featureService = featureService;
	}
	
}

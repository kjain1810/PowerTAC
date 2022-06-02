/*
 * Copyright (c) 2021-2022 by Sanjay Chandlekar
 */

package org.powertac.samplebroker.wholesalemarket;

import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.messages.BalancingMarketInformation;

import org.powertac.samplebroker.messages.*;
/*
 * TruthTelling (Team7) strategy, where a broker always reveal its true valuation while placing limitprice. 
 * Here, avg balancing price in the game can be used as a true valuation of the broker.
 * Following the Team7 strategy, the broker places one bid per auction and the remaining quantity as bid-quantity 
 * for all 24 auction instances.  
 */
public class Team7 extends Strategies
{
  private BalancingMarketInformation BMS;

  private static Team7 instance = null;
  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private Team7(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    BMS = this.messageManager.getBalancingMarketInformation();
  }

  public static Team7 getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new Team7(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  /*public Double computeQuantity()
    {

    }*/

  public Double activation(Double x)
  {
    return 1.0 / (1.0 + Math.exp(x));
  }

  public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    List<Double> limitPrices = new ArrayList<>();
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    if (remainingTries > 0)
    {
      // System.out.println("Inside the if condition");
      ClearedTradeInformation clearedTradeInformation = this.messageManager.getClearTradeInformation();
      Double sumPrices = 0.0;
      Double sumW = 0.0;

      WeatherInformation wi = this.messageManager.getWeatherInformation();
      Double curT = wi.getWeatherReport(currentTimeslot).getTemperature();
      Double curC = wi.getWeatherReport(currentTimeslot).getCloudCover();
      Double curWS = wi.getWeatherReport(currentTimeslot).getWindSpeed();

      // System.out.println("Got weather information");

      for (int i = timeslot - 24; i > 24; i -= 24)
      {
        Double act = 0.0;
        

        try{  
          
          Double lastT = wi.getWeatherReport(i).getTemperature();
          Double lastC = wi.getWeatherReport(i).getCloudCover();
          Double lastWS = wi.getWeatherReport(i).getWindSpeed();
          // System.out.println("Got timeslot weather info ");
          // System.out.println(i);

          Double diffT = lastT - curT;
          diffT /= 100;
          Double diffC = lastC - curC;
          diffC /= 100;
          Double diffWS = lastWS - curWS;
          diffWS /= 10;
          Double diffVec = diffT * diffT + diffC * diffC + diffWS * diffWS;
          diffVec = Math.sqrt(diffVec);
          act =  diffVec;
        } 
        catch (Error e) {System.out.println("Error on"); System.out.println(i);}
        

        Double mcp = clearedTradeInformation.getLastMCPForProximity(i - 1, 1);;
        // Double act = this.activation(diffVec);
        act = this.activation(act);
        sumPrices += mcp * act;
        sumW += act;
      }
      
      // System.out.println("Out of the for loop");
      Double avgSellingPrice = sumPrices / sumW;
      // System.out.println("Calculated price");

      if(remainingTries <= 6)
        avgSellingPrice -= (double)(24 - remainingTries);
      else  if(remainingTries <= 18)
        avgSellingPrice -= (double)(-2 * remainingTries + 24) / 3.0;
      else
        avgSellingPrice += remainingTries / 2.0;
      limitPrices.add(avgSellingPrice);
      // System.out.println("Readjusted price and added");

      return limitPrices;
    }
    else
      limitPrices.add(null); // market order
    
    // System.out.println("Returning with null?");

    return limitPrices;
  }

  public List<Double> computeQuantity(Integer timeslot, Integer currentTimeslot, Double amount)
  {
    List<Double> limitQuantities = new ArrayList<>();
    limitQuantities.add(amount);     
    return limitQuantities;
  }

  // Preparing the bid for future timeslot 'timeslot'
  public List<Order> submitBid(int timeslot, List<Double> neededMWh, List<Double> limitPrice)
  {
    List<Order> orders = new ArrayList<>();

    for(int i = 0; i < numberOfBidsPerAuction; i++)
      orders.add(new Order(this.broker.getBroker(), timeslot, neededMWh.get(i), limitPrice.get(i)));

    return orders;
  }
}

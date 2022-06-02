/*
 * Copyright (c) 2021-2022 by Sanjay Chandlekar
 */

package org.powertac.samplebroker.wholesalemarket;

import java.util.List;
import java.util.ArrayList;
import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.messages.BalancingMarketInformation;

import org.powertac.samplebroker.messages.*;
/*
 * TruthTelling (Team8) strategy, where a broker always reveal its true valuation while placing limitprice. 
 * Here, avg balancing price in the game can be used as a true valuation of the broker.
 * Following the Team8 strategy, the broker places one bid per auction and the remaining quantity as bid-quantity 
 * for all 24 auction instances.  
 */
public class Team8 extends Strategies
{
  private BalancingMarketInformation BMS;

  private static Team8 instance = null;
  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private Team8(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    BMS = this.messageManager.getBalancingMarketInformation();
  }

  public static Team8 getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new Team8(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  /*public Double computeQuantity()
    {

    }*/

  public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    List<Double> limitPrices = new ArrayList<>();
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    if (remainingTries > 0)
    {


      ClearedTradeInformation clearedTradeInformation = this.messageManager.getClearTradeInformation();
      Double sumPrices = 0.0;
      int cnt = 0;

      for(int i = currentTimeslot - 24; i >= 0; i -= 24)
      {
        sumPrices += clearedTradeInformation.getLastMCPForProximity(i, 0);
        cnt += 1;
      }

      Double avgSellingPrice = sumPrices / cnt;

      if(remainingTries <= 6)
        avgSellingPrice += (double)(24 - remainingTries);
      else  if(remainingTries <= 18)
        avgSellingPrice += (double)(-2 * remainingTries + 24) / 3.0;
      else
        avgSellingPrice -= remainingTries / 2.0;
      limitPrices.add(-avgSellingPrice);
      return limitPrices;
    }
    else
      limitPrices.add(null); // market order

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

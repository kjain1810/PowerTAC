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

/* 
* In the Linear strategy, a broker start with minimum[maximum] bid[ask] value and incrementaly
* increse[decrease] the bid[ask] values for each of next 23 auctions for the same future timeslot. 
* Following the Linear strategy, the broker places one bid per auction and the remaining quantity as bid-quantity 
* for all 24 auction instances.
*/

public class Linear extends Strategies
{
  private double step = 2.0;
  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private static Linear instance = null;

  private Linear(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
  }

  public static Linear getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new Linear(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    List<Double> limitPrices = new ArrayList<>();
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
      double startPrice = 0.0;

      if(amountNeeded > 0.0)
      {
        //Buyer
        startPrice = buyLimitPriceMax;
        limitPrices.add(Math.max(buyLimitPriceMin, (startPrice - step * (24 - remainingTries))));
      }
      else
      {
        //Seller
        startPrice = sellLimitPriceMax;
        limitPrices.add(Math.max(sellLimitPriceMin, (startPrice - step * (24 - remainingTries))));
      }
    }
    else
      limitPrices.add(null);  // market Order

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

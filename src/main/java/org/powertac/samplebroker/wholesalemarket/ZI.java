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
* The ZI strategy follows a randomized approach to bid in a PDA by ignoring the state of the market; 
* it samples a price from a uniform distribution between the minimum bid/ask price and maximum bid/ask price. 
* Following the ZI strategy, the broker places one bid per auction and the remaining quantity as bid-quantity 
* for all 24 auction instances.
*/
public class ZI extends Strategies
{
  private static ZI instance = null;
  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private ZI(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
  }

  public static ZI getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new ZI(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
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
      double minPrice = 0.0;
      double maxPrice = 0.0;

      if (amountNeeded > 0.0)
      {
        // buying
        maxPrice = this.buyLimitPriceMax;
        minPrice = this.buyLimitPriceMin;
      }
      else
      {
        // selling
        maxPrice = this.sellLimitPriceMax;
        minPrice = this.sellLimitPriceMin;
      }
      limitPrices.add(minPrice + Math.random()*(maxPrice - minPrice));
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

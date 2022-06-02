/*
* Copyright (c) 2021-2022 by Sanjay Chandlekar
*/

package org.powertac.samplebroker.wholesalemarket;

import java.util.List;
import org.powertac.common.Order;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

/*
* Superclass of all wholesale bidding strategies
* All the subclasses follow singleton design pattern
*/

abstract public class Strategies
{
  BrokerContext broker;
  double buyLimitPriceMax;
  double buyLimitPriceMin;
  double sellLimitPriceMax;
  double sellLimitPriceMin;
  MessageManager messageManager;

  public Strategies(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    this.broker = broker;
    this.buyLimitPriceMax = buyLimitPriceMax;
    this.buyLimitPriceMin = buyLimitPriceMin;
    this.sellLimitPriceMax = sellLimitPriceMax;
    this.sellLimitPriceMin = sellLimitPriceMin;
    this.messageManager = messageManager;
  }

  public List<Double> computeQuantity(Integer timeslot, Integer currentTimeslot, Double amount)
  {
      System.out.println("Handled in MarketManagerService !");
      return null;
  }

  abstract public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount);

  abstract public List<Order> submitBid(int timeslot, List<Double> neededMWh, List<Double> limitPrice);
}

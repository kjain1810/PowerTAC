/*
* Copyright (c) 2021-2022 by Sanjay Chandlekar
*/

package org.powertac.samplebroker.wholesalemarket;

import java.util.Map;
import java.util.List;
import javafx.util.Pair;
import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.messages.ClearedTradeInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;


/*
* The ZIP agent maintains a scalar variable m denoting the profit it aims to achieve, which gets 
* combined with a unit limit price to compute a bid price p. Small increments adjust the price for 
* each trade with the help of a delta by comparing the submitted bid price and the clearing price. 
* Following the ZIP strategy, the broker places one bid per auction and the remaining quantity as 
* bid-quantity for all 24 auction instances.
*/

public class ZIP extends Strategies
{
  public class LimitPriceDeterminants
  {
      public Double limitPrice;
      public Double profitMargin;
      public Double delta;
      public Integer executionTimeslot;

      public Double learningRate = 0.8;
      public Double momentumCoefficient = 0.5;

      public LimitPriceDeterminants(Double limitPrice, Double profitMargin, Integer executionTimeslot)
      {
          this.limitPrice = limitPrice;
          this.profitMargin = profitMargin;
          delta = 0.0;
          this.executionTimeslot = executionTimeslot;
      }
  }

  WholesaleMarketInformation WMS;
  ClearedTradeInformation CTI;

  private Map <Integer, LimitPriceDeterminants> bidPricebyDispatchTimeslot;
  private Map <Integer, LimitPriceDeterminants> askPricebyDispatchTimeslot;

  private static ZIP instance = null;
  private Random rand = new Random();

  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private ZIP(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);

    bidPricebyDispatchTimeslot = new HashMap<>();
    askPricebyDispatchTimeslot = new HashMap<>();

    WMS = this.messageManager.getWholesaleMarketInformation();
    CTI = this.messageManager.getClearTradeInformation();
  }

  public static ZIP getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new ZIP(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    List<Double> limitPrices = new ArrayList<>();
    double amountNeeded = amount[0];

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    double bid;

    if(remainingTries > 0)
    {
        if(amountNeeded > 0.0)
        {
          LimitPriceDeterminants lpd = this.bidPricebyDispatchTimeslot.get(timeslot);

          if(lpd == null)
          {
            lpd = new LimitPriceDeterminants(rand.nextDouble()*buyLimitPriceMin, -0.1 , currentTimeslot);
            this.bidPricebyDispatchTimeslot.put(timeslot, lpd);
            bid = lpd.limitPrice;
          }
          else
          {
            Map<Integer, Pair<Double, Double>> ctm = CTI.getClearedTradebyExecutionTimeslot(timeslot);

            Double lastMCP = 0.0;

            for(Map.Entry<Integer, Pair<Double, Double>> item : ctm.entrySet())
            {
              if((item.getKey()-1) == lpd.executionTimeslot)
                  lastMCP = item.getValue().getKey();
            }

            double sdelta = lpd.learningRate * (-lastMCP - lpd.limitPrice);               // For bid, limitPrice is negetive, so "-lastMCP"

            lpd.executionTimeslot = currentTimeslot;

            lpd.delta = lpd.momentumCoefficient * lpd.delta + (1 - lpd.momentumCoefficient) * sdelta;

            lpd.profitMargin = Math.max(-1.0, lpd.profitMargin + (lpd.delta/lpd.limitPrice));

            lpd.limitPrice = Math.max(buyLimitPriceMin, lpd.limitPrice * (1 + lpd.profitMargin));

            this.bidPricebyDispatchTimeslot.put(timeslot, lpd);

            bid = lpd.limitPrice;
          }
        }
        else
        {
          LimitPriceDeterminants lpd = askPricebyDispatchTimeslot.get(timeslot);

          if(lpd == null)
          {
            lpd = new LimitPriceDeterminants((sellLimitPriceMax + sellLimitPriceMin) / 2, 0.1 , currentTimeslot);
            askPricebyDispatchTimeslot.put(timeslot, lpd);
            bid = lpd.limitPrice;
          }
          else
          {
            Map<Integer, Pair<Double, Double>> ctm = CTI.getClearedTradebyExecutionTimeslot(timeslot);

            Double lastMCP = 0.0;

            for(Map.Entry<Integer, Pair<Double, Double>> item : ctm.entrySet())
            {
              if((item.getKey()-1) == lpd.executionTimeslot)
                lastMCP = item.getValue().getKey();
            }

            double sdelta = lpd.learningRate * (lastMCP - lpd.limitPrice);

            lpd.executionTimeslot = currentTimeslot;

            lpd.delta = lpd.momentumCoefficient * lpd.delta + (1 - lpd.momentumCoefficient) * sdelta;

            lpd.profitMargin = Math.max(-1.0, lpd.profitMargin + (lpd.delta/lpd.limitPrice));

            lpd.limitPrice = Math.min(sellLimitPriceMax, lpd.limitPrice * (1 + lpd.profitMargin));

            askPricebyDispatchTimeslot.put(timeslot, lpd);

            bid = lpd.limitPrice;
          }
          //System.out.println(timeslot + ", " + lpd.limitPrice + ", " + lpd.profitMargin + ", " + lpd.delta + ", " + lpd.executionTimeslot);
        }
        limitPrices.add(bid);
    }
    else
      limitPrices.add(null);   // market Order
    
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

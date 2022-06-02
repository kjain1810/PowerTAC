/*
* Copyright (c) 2021-2022 by Sanjay Chandlekar
*/

package org.powertac.samplebroker.wholesalemarket;

import java.util.List;
import java.util.ArrayList;
import org.powertac.common.Order;
import org.powertac.common.Competition;

import org.powertac.samplebroker.messages.*;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.MessageManager;

/*
* Write your wholesale bidding strategy here,
* in particular, complete the method computeLimitPrice() which calculates limitprices
* and computeQuantity() which calculates limit-quantities for each auction instance in the game
*/

/*
* Hints for designing your strategy:
  * You can utilise additional game information like past clearing patterns, weather patterns etc. using the messageMenager object (provided in commented form) 
  *
  * It is compulsary to buy all the required quantity, thus placing market order in the last opportunity.
  *
  * You can place multiple bids too for any auction instance, for that you need to decide multiple limitprices and  need to divide required quantity
  * into multiple bids. numberOfBidsPerAuction parameters controls this choice. 
  * One simple method to decide the limit-quantities for multiple bids may be divide the required quantity equally into numberOfBidsPerAuction bids
*/
public class Team1 extends Strategies
{
  private static Team1 instance = null;

  private Integer numberOfBidsPerAuction = 1;  // define the number of bids a broker places per auction instance

  private Team1(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    super(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
  }

  public static Team1 getInstance(BrokerContext broker, double buyLimitPriceMax, double buyLimitPriceMin, double sellLimitPriceMax, double sellLimitPriceMin, MessageManager messageManager)
  {
    if(instance == null)
    {
      instance = new Team1(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
    }
    return instance;
  }

  public List<Double> computeLimitPrice(int timeslot, int currentTimeslot, double ...amount)
  {
    /*
    * Input:
    * timeslot: future timeslot for which bid is getting places, can be upto 24 timeslots
                away from currentTimeslot 
    * currentTimeslot: current timeslot of the game
    * ...amount (varArgs): amount contains details of required quantity for a future timeslot 'timeslot'.
                           as this argument is varArgs, you can pass additional information of type 'double', too.
    *
    * Output:
    * limitPrices: limitPrices for (currentTimeslot, timeslot) pair
    */
    double amountNeeded = amount[0];

    // Initilising information class objects to be used here (uncomment if you want to use any of them)
    // GameInformation gameInformation = this.messageManager.getGameInformation();
    // ClearedTradeInformation clearedTradeInformation = this.messageManager.getClearTradeInformation();
    // MarketTransactionInformation marketTransactionInformation = this.messageManager.getMarketTransactionInformation();
    // BalancingMarketInformation balancingMarketInformation = this.messageManager.getBalancingMarketInformation();
    // MarketPositionInformation marketPositionInformation = this.messageManager.getMarketPositionInformation();
    // CashPositionInformation cashPositionInformation = this.messageManager.getCashPositionInformation();
    // WeatherInformation weatherInformation = this.messageManager.getWeatherInformation();

    int remainingTries = (timeslot - currentTimeslot - Competition.currentCompetition().getDeactivateTimeslotsAhead());

    if (remainingTries > 0)
    {
        List<Double> limitPrices = new ArrayList<>();

        //   your logic here (to decide the limitprices for numberOfBidsPerAuction bids/asks)
        for (int i = 0; i < 50; i++)
        limitPrices.add(40.0);        
        limitPrices.add(40.0);        
        limitPrices.add(40.0);        
        return limitPrices;
    }
    else
      return null; // market order
  }

  public List<Double> computeQuantity(Integer timeslot, Integer currentTimeslot, Double amount)
  {
    /*
    * Input:
    * timeslot: future timeslot for which bid is getting places, can be upto 24 timeslots
                away from currentTimeslot 
    * currentTimeslot: current timeslot of the game
    * amount: amount contains details of required quantity for a future timeslot 'timeslot'.
    *
    * Output:
    * limitQuantities: limitQuantities for (currentTimeslot, timeslot) pair
    */

    List<Double> limitQuantities = new ArrayList<>();
    
    // your logic here (to divide the required quantity into numberOfBidsPerAuction bids/asks)

    // default code
    limitQuantities.add(amount); // as default value of numberOfBidsPerAuction is 1, by all the required quantity in 1 bid
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

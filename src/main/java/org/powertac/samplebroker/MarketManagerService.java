/*
 * Copyright (c) 2012-2014 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
* Copyright (c) 2021-2022 by Sanjay Chandlekar
*/

package org.powertac.samplebroker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.powertac.common.Competition;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Timeslot;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.MessageManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.powertac.samplebroker.messages.ClearedTradeInformation;
import org.powertac.samplebroker.messages.GameInformation;
import org.powertac.samplebroker.wholesalemarket.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.powertac.samplebroker.information.SubmittedBidInformation;
import org.powertac.samplebroker.information.WholesaleMarketInformation;

// import com.mongodb.BasicDBObject;
// import com.mongodb.DB;
// import com.mongodb.DBObject;
// import com.mongodb.DBCollection;
// import com.mongodb.MongoClient;

/**
 * Handles market interactions on behalf of the broker.
 *
 * @author John Collins
 */

/**
  * Included additional methods for placing bids
  * @author Sanjay Chandlekar
  */
@Service
public class MarketManagerService implements MarketManager, Initializable, Activatable {
  static private Logger log = LogManager.getLogger(MarketManagerService.class);

  private BrokerContext broker; // broker

  @Autowired
  private MessageManager messageManager;

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;

  @Autowired
  private PortfolioManager portfolioManager;

  private GameInformation gameInformation;
  private ClearedTradeInformation clearedTradeInformation;
  private SubmittedBidInformation submittedBidInformation;
  private WholesaleMarketInformation wholesaleMarketInformation;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"

  @ConfigurableValue(valueType = "Double", description = "Upper end (least negative) of bid price range")
  private final double buyLimitPriceMax = -1.0; // broker pays

  @ConfigurableValue(valueType = "Double", description = "Lower end (most negative) of bid price range")
  private final double buyLimitPriceMin = -150.0; // broker pays

  @ConfigurableValue(valueType = "Double", description = "Upper end (most positive) of ask price range")
  private final double sellLimitPriceMax = 150.0; // other broker pays

  @ConfigurableValue(valueType = "Double", description = "Lower end (least positive) of ask price range")
  private final double sellLimitPriceMin = 0.5; // other broker pays

  @ConfigurableValue(valueType = "Double", description = "Minimum bid/ask quantity in MWh")
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  @ConfigurableValue(valueType = "Integer", description = "If set, seed the random generator")
  private final Integer seedNumber = null;

  // ---------------- local state ------------------
  private Random randomGen; // to randomize bid/ask prices

  // MongoDB client and Database
  // MongoClient mongoClient;

  // MongoDB database
  // DB mongoDatabase;

  String dbname; 

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;
  public double avgTimePerAction = 0.0;
  public int countAction = 0;

  public Double ea_miso_demand = 0.0;

  public MarketManagerService() {
    super();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.
   * SampleBroker)
   */
  @Override
  public void initialize(BrokerContext broker) {
    this.broker = broker;
    lastOrder = new HashMap<>();
    propertiesService.configureMe(this);
    System.out.println("  name=" + broker.getBrokerUsername());
    if (seedNumber != null) {
      System.out.println("  seeding=" + seedNumber);
      log.info("Seeding with : " + seedNumber);
      randomGen = new Random(seedNumber);
    } else {
      randomGen = new Random();
    }

    submittedBidInformation = new SubmittedBidInformation();

    // dbname = "IGT2022_Project";

    // try {
    //   mongoClient = new MongoClient("localhost", 27017);
    //   mongoDatabase = mongoClient.getDB(dbname);
    // } catch (Exception e) {
    //   log.warn("Mongo DB connection Exception " + e.toString());
    // }
    // log.info(" Connected to Database " + dbname + " -- Broker Initialize");
    // System.out.println("Connected to Database " + dbname + " from Initialize in MarketManagerService"); 
  }

  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
  @Override
  public double getMeanMarketPrice() {
    return meanMarketPrice;
  }

  @Override
  public SubmittedBidInformation getSubmittedBidInformation() {
    return submittedBidInformation;
  }

  // --------------- message handling -----------------
  /**
   * Handles the Competition instance that arrives at beginning of game. Here we
   * capture minimum order size to avoid running into the limit and generating
   * unhelpful error messages.
   */
  public synchronized void handleMessage(Competition comp) {
    minMWh = Math.max(minMWh, comp.getMinimumOrderQuantity());
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices for the
   * bootstrap period. We record the overall weighted mean price, as well as the
   * mean price and usage for a week.
   */
  public synchronized void handleMessage(MarketBootstrapData data) {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;

    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      } else {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
        marketMWh[index] = (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] = (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
  }

  /**
   * Receives a new MarketTransaction. We look to see whether an order we have
   * placed has cleared.
   */
  public synchronized void handleMessage(MarketTransaction tx) {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);
  }

  @Override
  public Double get_exponentialaverage_misodemand() {
    return ea_miso_demand;
  }

  // Stores the submitted bids information for each submission timeslot in the game
  /* public synchronized void handleMessage(TimeslotComplete ts)
  {
    int currentTimeslot = ts.getTimeslotIndex();
    gameInformation = messageManager.getGameInformation();

    try
    {
      String col6 = "Submitted_Bid_Information";
      DBCollection collection6 = mongoDatabase.getCollection(col6);

      Map<Integer, List<Pair<Double, Double>>> MTI = submittedBidInformation.getSubmittedBidInformationbyMessageTimeslot(currentTimeslot - 1);

      for (Map.Entry<Integer, List<Pair<Double, Double>>> message : MTI.entrySet())
      {
        DBObject document6 = new BasicDBObject();

        Double avgLimitPrice = 0.0;
        Double totalQuantity = 0.0;

        for(Pair<Double, Double> item : message.getValue())
        {
          if(item.getKey() < 0.0)
          {
              avgLimitPrice += item.getKey();
              totalQuantity += item.getValue();   
          }
        }  

        if(message.getValue().size() != 0)
            avgLimitPrice /= message.getValue().size();

        document6.put("Game_Name", gameInformation.getName());
        document6.put("Bidding_Timeslot", currentTimeslot - 1);
        document6.put("Execution_Timeslot", message.getKey());
        document6.put("LimitPrice", avgLimitPrice);
        document6.put("Broker's_Bidded_Quantity", totalQuantity);
        
        collection6.insert(document6);
      }
    }
    catch (Exception e)
    {
    }
  } */

  // ----------- per-timeslot activation ---------------

  /**
   * Compute needed quantities for each open timeslot, then submit orders for
   * those quantities.
   *
   * @see org.powertac.samplebroker.interfaces.Activatable#activate(int)
   */
  @Override
  public synchronized void activate (int timeslotIndex)
  {
    gameInformation = messageManager.getGameInformation();
    int numberOfBrokers = gameInformation.getBrokers().size() - 1;   // number of players in the game excluding default broker

    log.debug("Current timeslot is " + timeslotIndex);
    System.out.println("Current Timeslot : " + timeslotIndex);

    for (Timeslot timeslot : timeslotRepo.enabledTimeslots())
    {
      // int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
      // double neededMWh1 = portfolioManager.collectUsage(index) / 1000.0;
      double neededMWh = messageManager.collectNetDemand(timeslot.getSerialNumber()) / (numberOfBrokers*1000.0); // equally dividing total demand among all the players
      submitOrder(neededMWh , timeslot.getSerialNumber());                         
    }
  }

   /**
   * Composes and submits the appropriate order for the given timeslot.
   */
  private void submitOrder (double neededMWh, int timeslot)
  {
    double totalAmountNeeded = neededMWh;

    MarketPosition posn = broker.getBroker().findMarketPositionByTimeslot(timeslot);

    if (posn != null)
      neededMWh -= posn.getOverallBalance();          // subtract quantity that is already bought
    if (Math.abs(neededMWh) <= minMWh)
    {
      log.info("no power required in timeslot " + timeslot);
      return;
    }
    List<Order> orders = prepareOrder(timeslot, neededMWh, totalAmountNeeded);

    for(Order order: orders)
    {
      System.out.println("Submitted Bid: Broker : " + order.getBroker() + ", Timeslot : " + order.getTimeslotIndex() + ", Price : " + order.getLimitPrice() + ", Quantity : " + order.getMWh());
      lastOrder.put(timeslot, order);
      broker.sendMessage(order);
    }
  }

  private List<Order> prepareOrder(int timeslot, double amountNeeded, double totalAmountNeeded)
  {
    wholesaleMarketInformation = messageManager.getWholesaleMarketInformation();

    List<Double> limitPrice = null;
    List<Double> limitQuantity = null;
    List<Order> order = new ArrayList<>();

    int currentTimeslot = timeslotRepo.currentTimeslot().getSerialNumber();

    Strategies strategy;

    switch(broker.getBrokerUsername())
    {
      case "Team1":
                strategy = Team1.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;
      case "Team7":
                strategy = Team7.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;
      case "ZI":
                strategy = ZI.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;

      case "ZIP":
                strategy = ZIP.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;

      case "TT":
                strategy = TT.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;

      case "Linear":
                strategy = Linear.getInstance(broker, buyLimitPriceMax, buyLimitPriceMin, sellLimitPriceMax, sellLimitPriceMin, messageManager);
                limitPrice = strategy.computeLimitPrice(timeslot, currentTimeslot, amountNeeded);
                limitQuantity = strategy.computeQuantity(timeslot, currentTimeslot, amountNeeded);
                order = strategy.submitBid(timeslot, limitQuantity, limitPrice);
                break;

      default:
                double lp = computeLimitPrice(timeslot, amountNeeded);
                limitPrice.add(lp);
                limitQuantity.add(amountNeeded);
                order.add(new Order(this.broker.getBroker(), timeslot, amountNeeded, lp));
    }

    if(limitPrice == null)
    {
      wholesaleMarketInformation.setWholesaleMarketOrderMap(timeslot, amountNeeded);
    }

    submittedBidInformation.setSubmittedBidInformationbyExecutionTimeslot(timeslot, currentTimeslot, limitPrice, limitQuantity);
    submittedBidInformation.setSubmittedBidInformationbyMessageTimeslot(currentTimeslot, timeslot, limitPrice, limitQuantity);

    return order;
  }

  /**
  * Computes a limit price with a random element.
  */
  private Double computeLimitPrice (int timeslot, double amountNeeded)
  {
    log.debug("Compute limit for " + amountNeeded + ", timeslot " + timeslot);
    // start with default limits
    Double oldLimitPrice;
    double minPrice;
    if (amountNeeded > 0.0) {
      // buying
      oldLimitPrice = buyLimitPriceMax;
      minPrice = buyLimitPriceMin;
    }
    else {
      // selling
      oldLimitPrice = sellLimitPriceMax;
      minPrice = sellLimitPriceMin;
    }
    // check for escalation
    Order lastTry = lastOrder.get(timeslot);
    if (lastTry != null)
      log.debug("lastTry: " + lastTry.getMWh() +
                " at " + lastTry.getLimitPrice());
    if (lastTry != null
        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
      oldLimitPrice = lastTry.getLimitPrice();
      log.debug("old limit price: " + oldLimitPrice);
    }

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    int current = timeslotRepo.currentSerialNumber();
    int remainingTries = (timeslot - current
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    log.debug("remainingTries: " + remainingTries);
    if (remainingTries > 0) {
      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
      double computedPrice = oldLimitPrice + randomGen.nextDouble() * range; 
      return Math.max(newLimitPrice, computedPrice);
    }
    else
      return null; // market order
  }
 }

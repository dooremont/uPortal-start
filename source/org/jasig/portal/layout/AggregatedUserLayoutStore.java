/**
 * Copyright � 2002 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */




package org.jasig.portal.layout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.jasig.portal.ChannelDefinition;
import org.jasig.portal.ChannelParameter;
import org.jasig.portal.EntityIdentifier;
import org.jasig.portal.PortalException;
import org.jasig.portal.RDBMServices;
import org.jasig.portal.RDBMUserLayoutStore;
import org.jasig.portal.StructureStylesheetUserPreferences;
import org.jasig.portal.ThemeStylesheetUserPreferences;
import org.jasig.portal.ThemeStylesheetDescription;
import org.jasig.portal.StructureStylesheetDescription;
import org.jasig.portal.UserProfile;
import org.jasig.portal.groups.IEntityGroup;
import org.jasig.portal.groups.IGroupMember;
import org.jasig.portal.layout.restrictions.IUserLayoutRestriction;
import org.jasig.portal.layout.restrictions.PriorityRestriction;
import org.jasig.portal.layout.restrictions.UserLayoutRestriction;
import org.jasig.portal.layout.restrictions.UserLayoutRestrictionFactory;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.services.GroupService;
import org.jasig.portal.services.LogService;
import org.jasig.portal.utils.CommonUtils;



/**
 * AggregatedUserLayoutStore implementation using the relational database with SQL 92.
 * <p>
 * Company: Instructional Media &amp; Magic
 *
 * @author <a href="mailto:mvi@immagic.com">Michael Ivanov</a>
 * @version $Revision$
 */

public class AggregatedUserLayoutStore extends RDBMUserLayoutStore implements IAggregatedUserLayoutStore {

  private static final int LOST_FOLDER_ID = -1;


  protected static final String FRAGMENT_UPDATE_SQL = "UPDATE UP_FRAGMENTS SET NEXT_NODE_ID=?,PREV_NODE_ID=?,CHLD_NODE_ID=?,PRNT_NODE_ID=?,"+
                                                               "EXTERNAL_ID=?,CHAN_ID=?,NAME=?,TYPE=?,HIDDEN=?,IMMUTABLE=?,UNREMOVABLE=?,GROUP_KEY=?,"+
                                                               "PRIORITY=? WHERE FRAGMENT_ID=? AND NODE_ID=?";
  protected static final String LAYOUT_UPDATE_SQL = "UPDATE UP_LAYOUT_STRUCT_AGGR SET NEXT_NODE_ID=?,PREV_NODE_ID=?,CHLD_NODE_ID=?,PRNT_NODE_ID=?,"+
                                                               "EXTERNAL_ID=?,CHAN_ID=?,NAME=?,TYPE=?,HIDDEN=?,IMMUTABLE=?,UNREMOVABLE=?,GROUP_KEY=?,"+
                                                               "PRIORITY=?,FRAGMENT_ID=?,FRAGMENT_NODE_ID=? WHERE LAYOUT_ID=? AND USER_ID=? AND NODE_ID=?";
  protected static final String FRAGMENT_RESTRICTION_UPDATE_SQL = "UPDATE UP_FRAGMENT_RESTRICTIONS SET RESTRICTION_VALUE=?"+
                                  " WHERE FRAGMENT_ID=? AND NODE_ID=? AND RESTRICTION_TYPE=? AND RESTRICTION_TREE_PATH=?";
  protected static final String LAYOUT_RESTRICTION_UPDATE_SQL = "UPDATE UP_LAYOUT_RESTRICTIONS SET RESTRICTION_VALUE=?"+
                                  " WHERE LAYOUT_ID=? AND USER_ID=? AND NODE_ID=? AND RESTRICTION_TYPE=? AND RESTRICTION_TREE_PATH=?";
  protected static final String CHANNEL_PARAM_UPDATE_SQL = "UPDATE UP_CHANNEL_PARAM SET CHAN_PARM_DESC=?,CHAN_PARM_VAL=?,CHAN_PARM_OVRD=?" +
                                  " WHERE CHAN_ID=? AND CHAN_PARM_NM=?";
  protected static final String CHANNEL_UPDATE_SQL = "UPDATE UP_CHANNEL SET CHAN_TITLE=?,CHAN_NAME=?,CHAN_DESC=?,CHAN_CLASS=?,CHAN_TYPE_ID=?,"+
                      "CHAN_PUBL_ID=?,CHAN_PUBL_DT=?,CHAN_APVL_ID=?,CHAN_APVL_DT=?,CHAN_TIMEOUT=?,CHAN_EDITABLE=?,CHAN_HAS_HELP=?,CHAN_HAS_ABOUT=?,"+
                      "CHAN_FNAME=?,CHAN_SECURE=? WHERE CHAN_ID=?";
  protected static final String FRAGMENT_ADD_SQL = "INSERT INTO UP_FRAGMENTS (FRAGMENT_ID,NODE_ID,NEXT_NODE_ID,PREV_NODE_ID,CHLD_NODE_ID,PRNT_NODE_ID,"+
                                                               "EXTERNAL_ID,CHAN_ID,NAME,TYPE,HIDDEN,IMMUTABLE,UNREMOVABLE,GROUP_KEY,PRIORITY)"+
                                                               " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
  protected static final String LAYOUT_ADD_SQL = "INSERT INTO UP_LAYOUT_STRUCT_AGGR (LAYOUT_ID,USER_ID,NODE_ID,NEXT_NODE_ID,PREV_NODE_ID,CHLD_NODE_ID,PRNT_NODE_ID,"+
                                                               "EXTERNAL_ID,CHAN_ID,NAME,TYPE,HIDDEN,IMMUTABLE,UNREMOVABLE,GROUP_KEY,PRIORITY,FRAGMENT_ID,FRAGMENT_NODE_ID)"+
                                                               " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
  protected static final String FRAGMENT_RESTRICTION_ADD_SQL = "INSERT INTO UP_FRAGMENT_RESTRICTIONS (RESTRICTION_TYPE,NODE_ID,FRAGMENT_ID,RESTRICTION_VALUE,RESTRICTION_TREE_PATH)"+
                                                               " VALUES (?,?,?,?,?)";
  protected static final String LAYOUT_RESTRICTION_ADD_SQL = "INSERT INTO UP_LAYOUT_RESTRICTIONS (RESTRICTION_TYPE,LAYOUT_ID,USER_ID,NODE_ID,RESTRICTION_VALUE,RESTRICTION_TREE_PATH)"+
                                                               " VALUES (?,?,?,?,?,?)";
  protected static final String CHANNEL_PARAM_ADD_SQL = "INSERT INTO UP_CHANNEL_PARAM (CHAN_ID,CHAN_PARM_NM,CHAN_PARM_DESC,CHAN_PARM_VAL,CHAN_PARM_OVRD)"+
                                                        " VALUES (?,?,?,?,?)";
  protected static final String CHANNEL_ADD_SQL = "INSERT INTO UP_CHANNEL (CHAN_ID,CHAN_TITLE,CHAN_NAME,CHAN_DESC,CHAN_CLASS,CHAN_TYPE_ID,CHAN_PUBL_ID,"+
                                  "CHAN_PUBL_DT,CHAN_APVL_ID,CHAN_APVL_DT,CHAN_TIMEOUT,CHAN_EDITABLE,CHAN_HAS_HELP,CHAN_HAS_ABOUT,"+
                                  "CHAN_FNAME,CHAN_SECURE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static String fragmentJoinQuery = "";

  public AggregatedUserLayoutStore() throws Exception {
    super();
    RDBMServices.supportsOuterJoins = false;
    if (RDBMServices.supportsOuterJoins) {
      if (RDBMServices.joinQuery instanceof RDBMServices.JdbcDb) {
        RDBMServices.joinQuery.addQuery("layout_aggr",
          "{oj UP_LAYOUT_STRUCT_AGGR ULS LEFT OUTER JOIN UP_LAYOUT_PARAM USP ON ULS.USER_ID = USP.USER_ID AND ULS.NODE_ID = USP.STRUCT_ID} WHERE");
        fragmentJoinQuery =
            "{oj UP_FRAGMENTS UF LEFT OUTER JOIN UP_FRAGMENT_PARAM UFP ON UF.NODE_ID = UFP.NODE_ID AND UF.FRAGMENT_ID = UFP.FRAGMENT_ID} WHERE";

        //RDBMServices.joinQuery.addQuery("ss_struct", "{oj UP_SS_STRUCT USS LEFT OUTER JOIN UP_SS_STRUCT_PAR USP ON USS.SS_ID=USP.SS_ID} WHERE");
        //RDBMServices.joinQuery.addQuery("ss_theme", "{oj UP_SS_THEME UTS LEFT OUTER JOIN UP_SS_THEME_PARM UTP ON UTS.SS_ID=UTP.SS_ID} WHERE");
      } else if (RDBMServices.joinQuery instanceof RDBMServices.PostgreSQLDb) {
         RDBMServices.joinQuery.addQuery("layout_aggr",
          "UP_LAYOUT_STRUCT_AGGR ULS LEFT OUTER JOIN UP_LAYOUT_PARAM USP ON ULS.USER_ID = USP.USER_ID AND ULS.NODE_ID = USP.STRUCT_ID WHERE");
         fragmentJoinQuery =
             "UP_FRAGMENTS UF LEFT OUTER JOIN UP_FRAGMENT_PARAM UFP ON UF.NODE_ID = UFP.NODE_ID AND UF.FRAGMENT_ID = UFP.FRAGMENT_ID WHERE";
        //RDBMServices.joinQuery.addQuery("ss_struct", "UP_SS_STRUCT USS LEFT OUTER JOIN UP_SS_STRUCT_PAR USP ON USS.SS_ID=USP.SS_ID WHERE");
        //RDBMServices.joinQuery.addQuery("ss_theme", "UP_SS_THEME UTS LEFT OUTER JOIN UP_SS_THEME_PARM UTP ON UTS.SS_ID=UTP.SS_ID WHERE");
     } else if (RDBMServices.joinQuery instanceof RDBMServices.OracleDb) {
        RDBMServices.joinQuery.addQuery("layout_aggr",
          "UP_LAYOUT_STRUCT_AGGR ULS, UP_LAYOUT_PARAM USP WHERE ULS.NODE_ID = USP.STRUCT_ID(+) AND ULS.USER_ID = USP.USER_ID AND");
        fragmentJoinQuery =
          "UP_FRAGMENTS UF, UP_FRAGMENT_PARAM UFP WHERE UF.NODE_ID = UFP.NODE_ID(+) AND UF.FRAGMENT_ID = UFP.FRAGMENT_ID AND";
        //RDBMServices.joinQuery.addQuery("ss_struct", "UP_SS_STRUCT USS, UP_SS_STRUCT_PAR USP WHERE USS.SS_ID=USP.SS_ID(+) AND");
        //RDBMServices.joinQuery.addQuery("ss_theme", "UP_SS_THEME UTS, UP_SS_THEME_PARM UTP WHERE UTS.SS_ID=UTP.SS_ID(+) AND");
      } else {
        throw new Exception("Unknown database!");
      }
    }
  }

   /**
   * Return the Structure ID tag (Overloaded)
   * @param  structId
   * @param  chanId
   * @return ID tag
   */
  protected String getStructId(int structId, int chanId) {
      return structId+"";
  }

  public void setStructureStylesheetUserPreferences (IPerson person, int profileId, StructureStylesheetUserPreferences ssup) throws Exception {
    int userId = person.getID();
    Connection con = RDBMServices.getConnection();
    try {
      // Set autocommit false for the connection
      int stylesheetId = ssup.getStylesheetId();
      RDBMServices.setAutoCommit(con, false);
      Statement stmt = con.createStatement();
      try {
        // write out params
        for (Enumeration e = ssup.getParameterValues().keys(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          // see if the parameter was already there
          String sQuery = "SELECT PARAM_VAL FROM UP_SS_USER_PARM WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profileId
              + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND PARAM_NAME='" + pName + "'";
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
          ResultSet rs = stmt.executeQuery(sQuery);
          if (rs.next()) {
            // update
            sQuery = "UPDATE UP_SS_USER_PARM SET PARAM_VAL='" + ssup.getParameterValue(pName) + "' WHERE USER_ID=" + userId
                + " AND PROFILE_ID=" + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND PARAM_NAME='" + pName
                + "'";
          }
          else {
            // insert
            sQuery = "INSERT INTO UP_SS_USER_PARM (USER_ID,PROFILE_ID,SS_ID,SS_TYPE,PARAM_NAME,PARAM_VAL) VALUES (" + userId
                + "," + profileId + "," + stylesheetId + ",1,'" + pName + "','" + ssup.getParameterValue(pName) + "')";
          }
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
          stmt.executeUpdate(sQuery);
        }
        // write out folder attributes
        for (Enumeration e = ssup.getFolders(); e.hasMoreElements();) {
          String folderId = (String)e.nextElement();
          for (Enumeration attre = ssup.getFolderAttributeNames(); attre.hasMoreElements();) {
            String pName = (String)attre.nextElement();
            String pValue = ssup.getDefinedFolderAttributeValue(folderId, pName);
            if (pValue != null) {
              // store user preferences
              String sQuery = "SELECT PARAM_VAL FROM UP_SS_USER_ATTS WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profileId
                  + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND STRUCT_ID=" + folderId + " AND PARAM_NAME='" + pName
                  + "' AND PARAM_TYPE=2";
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
              ResultSet rs = stmt.executeQuery(sQuery);
              if (rs.next()) {
                // update
                sQuery = "UPDATE UP_SS_USER_ATTS SET PARAM_VAL='" + pValue + "' WHERE USER_ID=" + userId + " AND PROFILE_ID="
                    + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND STRUCT_ID=" + folderId + " AND PARAM_NAME='"
                    + pName + "' AND PARAM_TYPE=2";
              }
              else {
                // insert
                sQuery = "INSERT INTO UP_SS_USER_ATTS (USER_ID,PROFILE_ID,SS_ID,SS_TYPE,STRUCT_ID,PARAM_NAME,PARAM_TYPE,PARAM_VAL) VALUES ("
                    + userId + "," + profileId + "," + stylesheetId + ",1," + folderId + ",'" + pName + "',2,'" + pValue
                    + "')";
              }
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
              stmt.executeUpdate(sQuery);
            }
          }
        }
        // write out channel attributes
        for (Enumeration e = ssup.getChannels(); e.hasMoreElements();) {
          String channelId = (String)e.nextElement();
          for (Enumeration attre = ssup.getChannelAttributeNames(); attre.hasMoreElements();) {
            String pName = (String)attre.nextElement();
            String pValue = ssup.getDefinedChannelAttributeValue(channelId, pName);
            if (pValue != null) {
              // store user preferences
              String sQuery = "SELECT PARAM_VAL FROM UP_SS_USER_ATTS WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profileId
                  + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND STRUCT_ID=" + channelId + " AND PARAM_NAME='" + pName
                  + "' AND PARAM_TYPE=3";
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
              ResultSet rs = stmt.executeQuery(sQuery);
              if (rs.next()) {
                // update
                sQuery = "UPDATE UP_SS_USER_ATTS SET PARAM_VAL='" + pValue + "' WHERE USER_ID=" + userId + " AND PROFILE_ID="
                    + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND STRUCT_ID=" + channelId + " AND PARAM_NAME='"
                    + pName + "' AND PARAM_TYPE=3";
              }
              else {
                // insert
                sQuery = "INSERT INTO UP_SS_USER_ATTS (USER_ID,PROFILE_ID,SS_ID,SS_TYPE,STRUCT_ID,PARAM_NAME,PARAM_TYPE,PARAM_VAL) VALUES ("
                    + userId + "," + profileId + "," + stylesheetId + ",1," + channelId + ",'" + pName + "',3,'" + pValue
                    + "')";
              }
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setStructureStylesheetUserPreferences(): " + sQuery);
              stmt.executeUpdate(sQuery);
            }
          }
        }
        // Commit the transaction
        RDBMServices.commit(con);
      } catch (Exception e) {
        // Roll back the transaction
        RDBMServices.rollback(con);
        throw  e;
      } finally {
        stmt.close();
      }
    } finally {
      RDBMServices.releaseConnection(con);
    }
  }

  public void setThemeStylesheetUserPreferences (IPerson person, int profileId, ThemeStylesheetUserPreferences tsup) throws Exception {
    int userId = person.getID();
    Connection con = RDBMServices.getConnection();
    try {
      // Set autocommit false for the connection
      int stylesheetId = tsup.getStylesheetId();
      RDBMServices.setAutoCommit(con, false);
      Statement stmt = con.createStatement();
      try {
        // write out params
        for (Enumeration e = tsup.getParameterValues().keys(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          // see if the parameter was already there
          String sQuery = "SELECT PARAM_VAL FROM UP_SS_USER_PARM WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profileId
              + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2 AND PARAM_NAME='" + pName + "'";
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setThemeStylesheetUserPreferences(): " + sQuery);
          ResultSet rs = stmt.executeQuery(sQuery);
          if (rs.next()) {
            // update
            sQuery = "UPDATE UP_SS_USER_PARM SET PARAM_VAL='" + tsup.getParameterValue(pName) + "' WHERE USER_ID=" + userId
                + " AND PROFILE_ID=" + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2 AND PARAM_NAME='" + pName
                + "'";
          }
          else {
            // insert
            sQuery = "INSERT INTO UP_SS_USER_PARM (USER_ID,PROFILE_ID,SS_ID,SS_TYPE,PARAM_NAME,PARAM_VAL) VALUES (" + userId
                + "," + profileId + "," + stylesheetId + ",2,'" + pName + "','" + tsup.getParameterValue(pName) + "')";
          }
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setThemeStylesheetUserPreferences(): " + sQuery);
          stmt.executeUpdate(sQuery);
        }
        // write out channel attributes
        for (Enumeration e = tsup.getChannels(); e.hasMoreElements();) {
          String channelId = (String)e.nextElement();
          for (Enumeration attre = tsup.getChannelAttributeNames(); attre.hasMoreElements();) {
            String pName = (String)attre.nextElement();
            String pValue = tsup.getDefinedChannelAttributeValue(channelId, pName);
            if (pValue != null) {
              // store user preferences
              String sQuery = "SELECT PARAM_VAL FROM UP_SS_USER_ATTS WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profileId
                  + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2 AND STRUCT_ID=" + channelId.substring(1) + " AND PARAM_NAME='" + pName
                  + "' AND PARAM_TYPE=3";
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setThemeStylesheetUserPreferences(): " + sQuery);
              ResultSet rs = stmt.executeQuery(sQuery);
              if (rs.next()) {
                // update
                sQuery = "UPDATE UP_SS_USER_ATTS SET PARAM_VAL='" + pValue + "' WHERE USER_ID=" + userId + " AND PROFILE_ID="
                    + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2 AND STRUCT_ID=" + channelId.substring(1) + " AND PARAM_NAME='"
                    + pName + "' AND PARAM_TYPE=3";
              }
              else {
                // insert
                sQuery = "INSERT INTO UP_SS_USER_ATTS (USER_ID,PROFILE_ID,SS_ID,SS_TYPE,STRUCT_ID,PARAM_NAME,PARAM_TYPE,PARAM_VAL) VALUES ("
                    + userId + "," + profileId + "," + stylesheetId + ",2," + channelId.substring(1) + ",'" + pName + "',3,'" + pValue
                    + "')";
              }
              LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setThemeStylesheetUserPreferences(): " + sQuery);
              stmt.executeUpdate(sQuery);
            }
          }
        }
        // Commit the transaction
        RDBMServices.commit(con);
      } catch (Exception e) {
        // Roll back the transaction
        RDBMServices.rollback(con);
        throw  e;
      } finally {
        stmt.close();
      }
    } finally {
      RDBMServices.releaseConnection(con);
    }
  }


     /**
     * Add the new user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param node a <code>ALNode</code> object specifying the node
     * @return a <code>ALNode</code> object specifying the node with the generated node ID
     * @exception PortalException if an error occurs
     */
    public ALNode addUserLayoutNode (IPerson person, UserProfile profile, ALNode node ) throws PortalException {
     Connection con = RDBMServices.getConnection();

     try {

      RDBMServices.setAutoCommit(con,false);

      int nodeId = 0;
      int layoutId = 0;
      int userId = person.getID();
      IALNodeDescription nodeDesc = node.getNodeDescription();

      int fragmentId = CommonUtils.parseInt(nodeDesc.getFragmentId());
      int fragmentNodeId = CommonUtils.parseInt(nodeDesc.getFragmentNodeId());

      Statement stmt = con.createStatement();
      ResultSet rs;

        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:
      if ( fragmentId > 0 && fragmentNodeId <= 0 ) {

       // TO GET THE NEXT NODE ID FOR FRAGMENT NODES
       rs = stmt.executeQuery("SELECT MAX(NODE_ID) FROM UP_FRAGMENTS WHERE FRAGMENT_ID=" + fragmentId);
       if ( rs.next() )
         nodeId = rs.getInt(1) + 1;
       else
         nodeId = 1;

       if ( rs != null ) rs.close();

      } else {
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profile.getProfileId();
        LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::addUserLayoutNode(): " + subSelectString);
        rs = stmt.executeQuery(subSelectString);
        try {
            rs.next();
            layoutId = rs.getInt(1);
            if (rs.wasNull()) {
                layoutId = 0;
            }
        } finally {
            rs.close();
        }

          // Make sure the next struct id is set in case the user adds a channel
          String sQuery = "SELECT NEXT_STRUCT_ID FROM UP_USER WHERE USER_ID=" + userId;
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::addUserLayoutNode(): " + sQuery);

          rs = stmt.executeQuery(sQuery);
          try {
            rs.next();
            nodeId = rs.getInt(1)+1;
          } finally {
            rs.close();
          }

        sQuery = "UPDATE UP_USER SET NEXT_STRUCT_ID=" + nodeId + " WHERE USER_ID=" + userId;
        stmt.executeUpdate(sQuery);
       }

      PreparedStatement psAddNode, psAddRestriction;


       // Setting the node ID
       nodeDesc.setId(nodeId+"");

      if ( fragmentId > 0 && fragmentNodeId <= 0 )
         psAddNode = con.prepareStatement(FRAGMENT_ADD_SQL);
      else
         psAddNode = con.prepareStatement(LAYOUT_ADD_SQL);

      if ( fragmentId > 0 )
         psAddRestriction = con.prepareStatement(FRAGMENT_RESTRICTION_ADD_SQL);
      else
         psAddRestriction = con.prepareStatement(LAYOUT_RESTRICTION_ADD_SQL);


        PreparedStatement  psAddChannelParam = null, psAddChannel = null;

        /*if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
          int publishId = CommonUtils.parseInt(((IALChannelDescription)nodeDesc).getChannelPublishId());
          if ( publishId > 0 ) {
           rs = stmt.executeQuery("SELECT CHAN_ID FROM UP_CHANNEL WHERE CHAN_ID=" + publishId);
           try {
            if ( !rs.next() ) {
               psAddChannelParam = con.prepareStatement(CHANNEL_PARAM_ADD_SQL);
               psAddChannel = con.prepareStatement(CHANNEL_ADD_SQL);
            }
           } finally {
            rs.close();
           }
          }
        }*/


       if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
          IALChannelDescription channelDesc = (IALChannelDescription) nodeDesc;
          int publishId = CommonUtils.parseInt(channelDesc.getChannelPublishId());
          if ( publishId > 0 ) {
           rs = stmt.executeQuery("SELECT CHAN_NAME FROM UP_CHANNEL WHERE CHAN_ID=" + publishId);
           try {
            if ( rs.next() ) {
             channelDesc.setName(rs.getString(1));
             fillChannelDescription( channelDesc );
            }
           } finally {
            rs.close();
           }
          }
       }


      ALNode resultNode = addUserLayoutNode ( userId, layoutId, node, psAddNode, psAddRestriction, null, null );

      if ( psAddNode != null ) psAddNode.close();
      if ( psAddRestriction != null ) psAddRestriction.close();
      if ( psAddChannel != null ) psAddChannel.close();
      if ( psAddChannelParam != null ) psAddChannelParam.close();

      stmt.close();
      RDBMServices.commit(con);
      con.close();

      return resultNode;

     } catch (Exception e) {
        String errorMessage = e.getMessage();
        try { RDBMServices.rollback(con); } catch ( SQLException sqle ) {
           LogService.log(LogService.ERROR, sqle.toString() );
           errorMessage += ":" + sqle.getMessage();
        }
         throw new PortalException(errorMessage);
       }
    }

    /**
     * Add the new user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param node a <code>ALNode</code> object specifying the node
     * @return a <code>ALNode</code> object specifying the node with the generated node ID
     * @exception PortalException if an error occurs
     */
    private ALNode addUserLayoutNode ( int userId, int layoutId, ALNode node, PreparedStatement psAddNode, PreparedStatement psAddRestriction,
                                               PreparedStatement psAddChannel, PreparedStatement psAddChannelParam ) throws PortalException {

      IALNodeDescription nodeDesc = node.getNodeDescription();

      boolean isFolder = (node.getNodeType() == IUserLayoutNodeDescription.FOLDER);
      int fragmentId = CommonUtils.parseInt(nodeDesc.getFragmentId());
      int fragmentNodeId = CommonUtils.parseInt(nodeDesc.getFragmentNodeId());
      int nodeId = CommonUtils.parseInt(nodeDesc.getId());
      int tmpValue = -1;

    try {

      // if the node is in the fragment
      if ( fragmentId > 0 && fragmentNodeId <= 0 ) {

        psAddNode.setInt(1,fragmentId);
        psAddNode.setInt(2,nodeId);

        tmpValue = CommonUtils.parseInt(node.getNextNodeId());
        if ( tmpValue > 0 )
         psAddNode.setInt(3,tmpValue);
        else
         psAddNode.setNull(3,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getPreviousNodeId());
        if ( tmpValue > 0 )
         psAddNode.setInt(4,tmpValue);
        else
         psAddNode.setNull(4,Types.INTEGER);

        tmpValue = (isFolder)?CommonUtils.parseInt(((ALFolder)node).getFirstChildNodeId()):-1;
        if ( tmpValue > 0 )
         psAddNode.setInt(5,tmpValue);
        else
         psAddNode.setNull(5,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getParentNodeId());
        if ( tmpValue > 0 )
         psAddNode.setInt(6,tmpValue);
        else
         psAddNode.setNull(6,Types.INTEGER);


        psAddNode.setNull(7,Types.VARCHAR);

        tmpValue = (!isFolder)?CommonUtils.parseInt(((IALChannelDescription)nodeDesc).getChannelPublishId()):-1;
        if ( tmpValue > 0 )
         psAddNode.setInt(8,tmpValue);
        else
         psAddNode.setNull(8,Types.INTEGER);

        psAddNode.setString(9,nodeDesc.getName());
        if ( isFolder ) {
         IALFolderDescription folderDesc = (IALFolderDescription) nodeDesc;
         int type = folderDesc.getFolderType();
         switch ( type ) {
          case UserLayoutFolderDescription.HEADER_TYPE:
           psAddNode.setString(10,"header");
           break;
          case UserLayoutFolderDescription.FOOTER_TYPE:
           psAddNode.setString(10,"footer");
           break;
          default:
           psAddNode.setString(10,"regular");
         }
        } else
           psAddNode.setNull(10,Types.VARCHAR);

         psAddNode.setString(11,(nodeDesc.isHidden())?"Y":"N");
         psAddNode.setString(12,(nodeDesc.isImmutable())?"Y":"N");
         psAddNode.setString(13,(nodeDesc.isUnremovable())?"Y":"N");
         psAddNode.setString(14,nodeDesc.getGroup());
         /*if ( node.getFragmentId() != null )
          psLayout.setString(16,node.getFragmentId());
         else
          psLayout.setNull(16,Types.VARCHAR);*/
         psAddNode.setInt(15,node.getPriority());

         //execute update layout
         psAddNode.executeUpdate();

      // if fragment ID < 0
      } else {

        psAddNode.setInt(1,layoutId);
        psAddNode.setInt(2,userId);
        psAddNode.setInt(3,nodeId);

        tmpValue = CommonUtils.parseInt(node.getNextNodeId());
        if ( tmpValue > 0 )
         psAddNode.setInt(4,tmpValue);
        else
         psAddNode.setNull(4,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getPreviousNodeId());
        if ( tmpValue > 0 )
         psAddNode.setInt(5,tmpValue);
        else
         psAddNode.setNull(5,Types.INTEGER);


        tmpValue = (isFolder)?CommonUtils.parseInt(((ALFolder)node).getFirstChildNodeId()):-1;
        if ( tmpValue > 0 )
         psAddNode.setInt(6,tmpValue);
        else
         psAddNode.setNull(6,Types.INTEGER);

        String parentId = node.getParentNodeId();
        if ( !IALFolderDescription.ROOT_FOLDER_ID.equals(parentId) )
         psAddNode.setInt(7,CommonUtils.parseInt(parentId,LOST_FOLDER_ID));
        else
         psAddNode.setNull(7,Types.INTEGER);

        psAddNode.setNull(8,Types.VARCHAR);

        tmpValue = (!isFolder)?CommonUtils.parseInt(((IALChannelDescription)nodeDesc).getChannelPublishId()):-1;
        if ( tmpValue > 0 )
         psAddNode.setInt(9,tmpValue);
        else
         psAddNode.setNull(9,Types.INTEGER);

        psAddNode.setString(10,nodeDesc.getName());

        if ( isFolder ) {
         IALFolderDescription folderDesc = (IALFolderDescription) nodeDesc;
         int type = folderDesc.getFolderType();
         switch ( type ) {
          case UserLayoutFolderDescription.HEADER_TYPE:
           psAddNode.setString(11,"header");
           break;
          case UserLayoutFolderDescription.FOOTER_TYPE:
           psAddNode.setString(11,"footer");
           break;
          default:
           psAddNode.setString(11,"regular");
         }
        } else
           psAddNode.setNull(11,Types.VARCHAR);

         psAddNode.setString(12,(nodeDesc.isHidden())?"Y":"N");
         psAddNode.setString(13,(nodeDesc.isImmutable())?"Y":"N");
         psAddNode.setString(14,(nodeDesc.isUnremovable())?"Y":"N");
         psAddNode.setString(15,nodeDesc.getGroup());
         /*if ( node.getFragmentId() != null )
          psLayout.setString(16,node.getFragmentId());
         else
          psLayout.setNull(16,Types.VARCHAR);*/
         psAddNode.setInt(16,node.getPriority());
         if ( fragmentId > 0 )
          psAddNode.setInt(17,fragmentId);
         else
          psAddNode.setNull(17,Types.INTEGER);

         if ( fragmentNodeId > 0 )
          psAddNode.setInt(18,fragmentNodeId);
         else
          psAddNode.setNull(18,Types.INTEGER);


         //execute update layout
         psAddNode.executeUpdate();
       }

         // Insert node restrictions
        Hashtable restrHash = nodeDesc.getRestrictions();
        if ( restrHash != null ) {

         PreparedStatement psRestr = null;


         if ( fragmentId > 0 ) {

          /*Enumeration restrictions = restrHash.elements();
           for ( ;restrictions.hasMoreElements(); ) {
             IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

             psAddRestriction.setInt(1,restriction.getRestrictionType());
             psAddRestriction.setInt(2,nodeId);
             psAddRestriction.setInt(3,fragmentId);
             psAddRestriction.setString(4,restriction.getRestrictionExpression());

              String path = restriction.getRestrictionPath();
              psAddRestriction.setString(5,path);

             //execute update restrictions
             psAddRestriction.executeUpdate();

           } // end for*/

         } else {

            Enumeration restrictions = restrHash.elements();
            for ( ;restrictions.hasMoreElements(); ) {
             IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

             psAddRestriction.setInt(1,restriction.getRestrictionType());
             psAddRestriction.setInt(2,layoutId);
             psAddRestriction.setInt(3,userId);
             psAddRestriction.setInt(4,nodeId);
             psAddRestriction.setString(5,restriction.getRestrictionExpression());

             String path = restriction.getRestrictionPath();
             psAddRestriction.setString(6,path);

             //execute update restrictions
             psAddRestriction.executeUpdate();

            } // end for

           } // end else


        } // end if



       // if we have channel parameters
       if ( !isFolder && psAddChannel != null && psAddChannelParam != null ) {

         IALChannelDescription channelDesc = (IALChannelDescription) nodeDesc;

         int publishId = CommonUtils.parseInt(channelDesc.getChannelPublishId());
         if ( publishId > 0 ) {

          for ( Enumeration paramNames = channelDesc.getParameterNames(); paramNames.hasMoreElements(); ) {
            String paramName = (String) paramNames.nextElement();
            String paramValue = channelDesc.getParameterValue(paramName);

            psAddChannelParam.setInt(1,publishId);

            psAddChannelParam.setString(2,paramName);
            if ( channelDesc.getDescription() != null )
             psAddChannelParam.setString(3,channelDesc.getDescription());
            else
             psAddChannelParam.setNull(3,Types.VARCHAR);
            psAddChannelParam.setString(4,paramValue);
            psAddChannelParam.setString(5,(channelDesc.canOverrideParameter(paramName))?"Y":"N");

            //execute update parameters
            psAddChannelParam.executeUpdate();
          }

             // Inserting channel attributes
            psAddChannel.setInt(1,publishId);

             psAddChannel.setString(2,channelDesc.getTitle());
             psAddChannel.setString(3,channelDesc.getName());
             if ( channelDesc.getDescription() != null )
              psAddChannel.setString(4,channelDesc.getDescription());
             else
              psAddChannel.setNull(4,Types.VARCHAR);
             psAddChannel.setString(5,channelDesc.getClassName());
             tmpValue = CommonUtils.parseInt(channelDesc.getChannelTypeId());
             if ( tmpValue > 0 )
              psAddChannel.setInt(6,tmpValue);
             else
              psAddChannel.setNull(6,Types.INTEGER);

             tmpValue = CommonUtils.parseInt(channelDesc.getChannelPublishId());
             if ( tmpValue > 0 )
              psAddChannel.setInt(7,tmpValue);
             else
              psAddChannel.setNull(7,Types.INTEGER);

             Timestamp timestamp = new java.sql.Timestamp(new Date().getTime());
             psAddChannel.setTimestamp(8,timestamp);
             psAddChannel.setInt(9,0);
             psAddChannel.setTimestamp(10,timestamp);
             psAddChannel.setInt(11,(int)channelDesc.getTimeout());
             psAddChannel.setString(12,(channelDesc.isEditable())?"Y":"N");
             psAddChannel.setString(13,(channelDesc.hasHelp())?"Y":"N");
             psAddChannel.setString(14,(channelDesc.hasAbout())?"Y":"N");
             psAddChannel.setString(15,channelDesc.getFunctionalName());

             //execute update parameters
             psAddChannel.executeUpdate();
         }
        }

        return node;

     } catch (Exception e) {
        e.printStackTrace();
        String errorMessage = e.getMessage();
        throw new PortalException(errorMessage);
       }

    }


  /**
     * Update the new user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param node a <code>ALNode</code> object specifying the node
     * @return a boolean result of this operation
     * @exception PortalException if an error occurs
     */
  public boolean updateUserLayoutNode (IPerson person, UserProfile profile, ALNode node ) throws PortalException {

     Connection con = RDBMServices.getConnection();

    try {

     RDBMServices.setAutoCommit(con,false);


      int userId = person.getID();
      int nodeId = CommonUtils.parseInt(node.getId());
      IALNodeDescription nodeDesc = node.getNodeDescription();

      Statement stmt = con.createStatement();

        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profile.getProfileId();
        //LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getUserLayout(): " + subSelectString);
        int layoutId;
        ResultSet rs = stmt.executeQuery(subSelectString);
        try {
            rs.next();
            layoutId = rs.getInt(1);
            if (rs.wasNull()) {
                layoutId = 0;
            }
        } finally {
            rs.close();
        }

      PreparedStatement psUpdateNode, psUpdateRestriction;
      int fragmentId = CommonUtils.parseInt(nodeDesc.getFragmentId());
      int fragmentNodeId = CommonUtils.parseInt(nodeDesc.getFragmentNodeId());

      if ( fragmentId > 0 && fragmentNodeId <= 0 )
         psUpdateNode = con.prepareStatement(FRAGMENT_UPDATE_SQL);
      else
         psUpdateNode = con.prepareStatement(LAYOUT_UPDATE_SQL);

      if ( fragmentId > 0 )
       psUpdateRestriction = con.prepareStatement(FRAGMENT_RESTRICTION_UPDATE_SQL);
      else
       psUpdateRestriction = con.prepareStatement(LAYOUT_RESTRICTION_UPDATE_SQL);

      PreparedStatement  psUpdateChannelParam = con.prepareStatement(CHANNEL_PARAM_UPDATE_SQL);
      PreparedStatement  psUpdateChannel = con.prepareStatement(CHANNEL_UPDATE_SQL);

      boolean result = updateUserLayoutNode ( userId, layoutId, node, psUpdateNode, psUpdateRestriction, null, null );

      if ( psUpdateNode != null ) psUpdateNode.close();
      if ( psUpdateRestriction != null ) psUpdateRestriction.close();
      if ( psUpdateChannel != null ) psUpdateChannel.close();
      if ( psUpdateChannelParam != null ) psUpdateChannelParam.close();


      RDBMServices.commit(con);

      // Closing
      stmt.close();
      con.close();

      return result;

     } catch (Exception e) {
        String errorMessage = e.getMessage();
        try { RDBMServices.rollback(con); } catch ( SQLException sqle ) {
           LogService.log(LogService.ERROR, sqle.toString() );
           errorMessage += ":" + sqle.getMessage();
        }
         throw new PortalException(errorMessage);
       }
 }

    /**
     * Update the new user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param node a <code>ALNode</code> object specifying the node
     * @return a boolean result of this operation
     * @exception PortalException if an error occurs
     */
  private boolean updateUserLayoutNode (int userId, int layoutId, ALNode node, PreparedStatement psUpdateNode,
                  PreparedStatement psUpdateRestriction, PreparedStatement psUpdateChannel, PreparedStatement psUpdateChannelParam ) throws PortalException {
      //boolean layoutUpdate = false, channelUpdate = false, paramUpdate = false, restrUpdate = false;

      int count = 0;

      boolean isFolder = (node.getNodeType() == IUserLayoutNodeDescription.FOLDER);
      IALNodeDescription nodeDesc = node.getNodeDescription();
      int nodeId = CommonUtils.parseInt(nodeDesc.getId());
      int fragmentId = CommonUtils.parseInt(nodeDesc.getFragmentId());
      int fragmentNodeId = CommonUtils.parseInt(nodeDesc.getFragmentNodeId());
      int tmpValue = -1;

   try {

     if ( fragmentId > 0 && fragmentNodeId <= 0 ) {

        tmpValue = CommonUtils.parseInt(node.getNextNodeId());
        if ( tmpValue > 0 )
         psUpdateNode.setInt(1,tmpValue);
        else
         psUpdateNode.setNull(1,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getPreviousNodeId());
        if ( tmpValue > 0 )
         psUpdateNode.setInt(2,tmpValue);
        else
         psUpdateNode.setNull(2,Types.INTEGER);

        tmpValue = (isFolder)?CommonUtils.parseInt(((ALFolder)node).getFirstChildNodeId()):-1;
        if ( tmpValue > 0 )
         psUpdateNode.setInt(3,tmpValue);
        else
         psUpdateNode.setNull(3,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getParentNodeId());
        if ( tmpValue > 0 )
         psUpdateNode.setInt(4,tmpValue);
        else
         psUpdateNode.setNull(4,Types.INTEGER);


        psUpdateNode.setNull(5,Types.VARCHAR);

        tmpValue = (!isFolder)?CommonUtils.parseInt(((IALChannelDescription)nodeDesc).getChannelPublishId()):-1;
        if ( tmpValue > 0 )
         psUpdateNode.setInt(6,tmpValue);
        else
         psUpdateNode.setNull(6,Types.INTEGER);

        psUpdateNode.setString(7,nodeDesc.getName());
        if ( isFolder ) {
         IALFolderDescription folderDesc = (IALFolderDescription) nodeDesc;
         int type = folderDesc.getFolderType();
         switch ( type ) {
          case UserLayoutFolderDescription.HEADER_TYPE:
           psUpdateNode.setString(8,"header");
           break;
          case UserLayoutFolderDescription.FOOTER_TYPE:
           psUpdateNode.setString(8,"footer");
           break;
          default:
           psUpdateNode.setString(8,"regular");
         }
        } else
           psUpdateNode.setNull(8,Types.VARCHAR);

         psUpdateNode.setString(9,(nodeDesc.isHidden())?"Y":"N");
         psUpdateNode.setString(10,(nodeDesc.isImmutable())?"Y":"N");
         psUpdateNode.setString(11,(nodeDesc.isUnremovable())?"Y":"N");
         psUpdateNode.setString(12,nodeDesc.getGroup());

         psUpdateNode.setInt(13,node.getPriority());

         psUpdateNode.setInt(14,nodeId);
         psUpdateNode.setInt(15,fragmentId);

         //execute update layout
         count += psUpdateNode.executeUpdate();

       // if fragment id <= 0
     } else {

        tmpValue = CommonUtils.parseInt(node.getNextNodeId());
        if ( tmpValue > 0 )
         psUpdateNode.setInt(1,tmpValue);
        else
         psUpdateNode.setNull(1,Types.INTEGER);

        tmpValue = CommonUtils.parseInt(node.getPreviousNodeId());
        if ( tmpValue > 0 )
         psUpdateNode.setInt(2,tmpValue);
        else
         psUpdateNode.setNull(2,Types.INTEGER);

        tmpValue = (isFolder)?CommonUtils.parseInt(((ALFolder)node).getFirstChildNodeId()):-1;
        if ( tmpValue > 0 )
         psUpdateNode.setInt(3,tmpValue);
        else
         psUpdateNode.setNull(3,Types.INTEGER);

        String parentId = node.getParentNodeId();
        if ( !IALFolderDescription.ROOT_FOLDER_ID.equals(parentId) )
         psUpdateNode.setInt(4,CommonUtils.parseInt(parentId,LOST_FOLDER_ID));
        else
         psUpdateNode.setNull(4,Types.INTEGER);

        psUpdateNode.setNull(5,Types.VARCHAR);

        tmpValue = (!isFolder)?CommonUtils.parseInt(((IALChannelDescription)nodeDesc).getChannelPublishId()):-1;
        if ( tmpValue > 0 )
         psUpdateNode.setInt(6,tmpValue);
        else
         psUpdateNode.setNull(6,Types.INTEGER);

        psUpdateNode.setString(7,nodeDesc.getName());

        if ( isFolder ) {
         IALFolderDescription folderDesc = (IALFolderDescription) nodeDesc;
         int type = folderDesc.getFolderType();
         switch ( type ) {
          case UserLayoutFolderDescription.HEADER_TYPE:
           psUpdateNode.setString(8,"header");
           break;
          case UserLayoutFolderDescription.FOOTER_TYPE:
           psUpdateNode.setString(8,"footer");
           break;
          default:
           psUpdateNode.setString(8,"regular");
         }
        } else
           psUpdateNode.setNull(8,Types.VARCHAR);

         psUpdateNode.setString(9,(nodeDesc.isHidden())?"Y":"N");
         psUpdateNode.setString(10,(nodeDesc.isImmutable())?"Y":"N");
         psUpdateNode.setString(11,(nodeDesc.isUnremovable())?"Y":"N");
         psUpdateNode.setString(12,nodeDesc.getGroup());
         /*if ( node.getFragmentId() != null )
          psLayout.setString(13,node.getFragmentId());
         else
          psLayout.setNull(13,Types.VARCHAR);*/

         psUpdateNode.setInt(13,node.getPriority());

         if ( fragmentId > 0 )
          psUpdateNode.setInt(14,fragmentId);
         else
          psUpdateNode.setNull(14,Types.INTEGER);

         if ( fragmentNodeId > 0 )
          psUpdateNode.setInt(15,fragmentNodeId);
         else
          psUpdateNode.setNull(15,Types.INTEGER);

         psUpdateNode.setInt(16,layoutId);
         psUpdateNode.setInt(17,userId);
         psUpdateNode.setInt(18,nodeId);

         //execute update layout
         count += psUpdateNode.executeUpdate();

       }

         // Insert node restrictions
         Hashtable restrHash = nodeDesc.getRestrictions();
         if ( restrHash != null ) {

          if ( fragmentId > 0 ) {

           /*Enumeration restrictions = restrHash.elements();
           for ( ;restrictions.hasMoreElements(); ) {
            IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

            psUpdateRestriction.setString(1,restriction.getRestrictionExpression());
            psUpdateRestriction.setInt(2,fragmentId);
            psUpdateRestriction.setInt(3,nodeId);
            psUpdateRestriction.setInt(4,restriction.getRestrictionType());

            String path = restriction.getRestrictionPath();
            psUpdateRestriction.setString(5,path);

            //execute update restrictions
            count += psUpdateRestriction.executeUpdate();
           } // end for */

          } else {

           Enumeration restrictions = restrHash.elements();
           for ( ;restrictions.hasMoreElements(); ) {
            IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

            psUpdateRestriction.setString(1,restriction.getRestrictionExpression());
            psUpdateRestriction.setInt(2,layoutId);
            psUpdateRestriction.setInt(3,userId);
            psUpdateRestriction.setInt(4,nodeId);
            psUpdateRestriction.setInt(5,restriction.getRestrictionType());

            String path = restriction.getRestrictionPath();
            psUpdateRestriction.setString(6,path);

            //execute update restrictions
            count += psUpdateRestriction.executeUpdate();

           } // end for
          }  // end else
         } // end if


        // if we have channel parameters

        /*if ( !isFolder ) {
         IALChannelDescription channelDesc = (IALChannelDescription) nodeDesc;
         int publishId = CommonUtils.parseInt(channelDesc.getChannelPublishId());
         if ( publishId > 0 ) {

          for ( Enumeration paramNames = channelDesc.getParameterNames(); paramNames.hasMoreElements(); ) {
            String paramName = (String) paramNames.nextElement();
            String paramValue = channelDesc.getParameterValue(paramName);

            if ( channelDesc.getDescription() != null )
             psUpdateChannelParam.setString(1,channelDesc.getDescription());
            else
             psUpdateChannelParam.setNull(1,Types.VARCHAR);
            psUpdateChannelParam.setString(2,paramValue);
            psUpdateChannelParam.setString(3,(channelDesc.canOverrideParameter(paramName))?"Y":"N");

             psUpdateChannelParam.setInt(4,publishId);

             psUpdateChannelParam.setString(5,paramName);

            //execute update parameters
            count += psUpdateChannelParam.executeUpdate();
          }

           // Inserting channel attributes
             psUpdateChannel.setString(1,channelDesc.getTitle());
             psUpdateChannel.setString(2,channelDesc.getName());
             if ( channelDesc.getDescription() != null )
              psUpdateChannel.setString(3,channelDesc.getDescription());
             else
              psUpdateChannel.setNull(3,Types.VARCHAR);
             psUpdateChannel.setString(4,channelDesc.getClassName());

             tmpValue = CommonUtils.parseInt(channelDesc.getChannelTypeId());
             if ( tmpValue > 0 )
              psUpdateChannel.setInt(5,tmpValue);
             else
              psUpdateChannel.setNull(5,Types.INTEGER);

             tmpValue = CommonUtils.parseInt(channelDesc.getChannelPublishId());
             if ( tmpValue > 0 )
              psUpdateChannel.setInt(6,tmpValue);
             else
              psUpdateChannel.setNull(6,Types.INTEGER);

             Timestamp timestamp = new java.sql.Timestamp(new Date().getTime());
             psUpdateChannel.setTimestamp(7,timestamp);
             psUpdateChannel.setInt(8,0);
             psUpdateChannel.setTimestamp(9,timestamp);
             psUpdateChannel.setInt(10,(int)channelDesc.getTimeout());
             psUpdateChannel.setString(11,(channelDesc.isEditable())?"Y":"N");
             psUpdateChannel.setString(12,(channelDesc.hasHelp())?"Y":"N");
             psUpdateChannel.setString(13,(channelDesc.hasAbout())?"Y":"N");
             psUpdateChannel.setString(14,channelDesc.getFunctionalName());

             psUpdateChannel.setInt(15,publishId);

             //execute update parameters
             count += psUpdateChannel.executeUpdate();
             //psChan.close();
         }
        }*/

        return count > 0;

     } catch (Exception e) {
        e.printStackTrace();
        String errorMessage = e.getMessage();
        throw new PortalException(errorMessage);
       }
  }

    /**
     * Delete the new user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param node a <code>ALNode</code> node ID specifying the node
     * @return a boolean result of this operation
     * @exception PortalException if an error occurs
     */
    public boolean deleteUserLayoutNode (IPerson person, UserProfile profile, ALNode node ) throws PortalException {
     Connection con = RDBMServices.getConnection();

     try {

      RDBMServices.setAutoCommit(con,false);

      int count = 0;

      int userId = person.getID();
      int nodeId = CommonUtils.parseInt(node.getId());
      IALNodeDescription nodeDesc = node.getNodeDescription();
      Statement stmt = con.createStatement();

        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profile.getProfileId();
        LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::deleteUserLayoutNode(): " + subSelectString);
        int layoutId;
        ResultSet rs = stmt.executeQuery(subSelectString);
        try {
            rs.next();
            layoutId = rs.getInt(1);
            if (rs.wasNull()) {
                layoutId = 0;
            }
        } finally {
            rs.close();
        }


      boolean isFolder = (node.getNodeType() == IUserLayoutNodeDescription.FOLDER);
      int fragmentId = CommonUtils.parseInt(nodeDesc.getFragmentId());
      int fragmentNodeId = CommonUtils.parseInt(nodeDesc.getFragmentNodeId());
      int tmpValue = -1;

      // if we have a channel
      /*  if ( !isFolder ) {
          IALChannelDescription channelDesc = (IALChannelDescription) nodeDesc;
          PreparedStatement  psParam =
             con.prepareStatement("DELETE FROM UP_CHANNEL_PARAM WHERE CHAN_ID=?,CHAN_PARM_NM=?");
          tmpValue = CommonUtils.parseInt(channelDesc.getChannelPublishId());
          for ( Enumeration paramNames = channelDesc.getParameterNames(); paramNames.hasMoreElements(); ) {
            String paramName = (String) paramNames.nextElement();

            if ( tmpValue > 0 )
             psParam.setInt(1,tmpValue);
            else
             psParam.setNull(1,Types.INTEGER);

             psParam.setString(2,paramName);

            //execute update parameters
            count += psParam.executeUpdate();
          }
            psParam.close();

           // deleting channel attributes
           PreparedStatement  psChan =
             con.prepareStatement("DELETE FROM UP_CHANNEL WHERE CHAN_ID=?");

             if ( tmpValue > 0 )
              psChan.setInt(1,tmpValue);
             else
              psChan.setNull(1,Types.INTEGER);

             //execute update parameters
             count += psChan.executeUpdate();
             psChan.close();

        }*/


         // Delete node restrictions
         Hashtable restrHash = nodeDesc.getRestrictions();
         if ( restrHash != null ) {

          if ( fragmentId > 0 ) {

           PreparedStatement  psFragmentRestr =
             con.prepareStatement("DELETE FROM UP_FRAGMENT_RESTRICTIONS"+
                                  " WHERE FRAGMENT_ID=? AND NODE_ID=? AND RESTRICTION_TYPE=? AND RESTRICTION_TREE_PATH=?");
           Enumeration restrictions = restrHash.elements();
           for ( ;restrictions.hasMoreElements(); ) {
            IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

            psFragmentRestr.setInt(1,fragmentId);
            psFragmentRestr.setInt(2,nodeId);
            psFragmentRestr.setInt(3,restriction.getRestrictionType());

            String path = restriction.getRestrictionPath();
            psFragmentRestr.setString(4,path);

            //execute update restrictions
            count += psFragmentRestr.executeUpdate();

           } // end for
            psFragmentRestr.close();

          // fragment ID is null
          } else {

           PreparedStatement  psRestr =
             con.prepareStatement("DELETE FROM UP_LAYOUT_RESTRICTIONS"+
                                  " WHERE LAYOUT_ID=? AND USER_ID=? AND NODE_ID=? AND RESTRICTION_TYPE=? AND RESTRICTION_TREE_PATH=?");

           Enumeration restrictions = restrHash.elements();
           for ( ;restrictions.hasMoreElements(); ) {
            IUserLayoutRestriction restriction = (IUserLayoutRestriction) restrictions.nextElement();

            psRestr.setInt(1,layoutId);
            psRestr.setInt(2,userId);
            psRestr.setInt(3,nodeId);
            psRestr.setInt(4,restriction.getRestrictionType());

            String path = restriction.getRestrictionPath();
            psRestr.setString(5,path);

            //execute update restrictions
            count += psRestr.executeUpdate();

           } // end for
            psRestr.close();
          } // end if for fragment ID
         } // end if


      if ( fragmentId > 0 && fragmentNodeId <= 0 ) {
       PreparedStatement  psFragment =
        con.prepareStatement("DELETE FROM UP_FRAGMENTS WHERE NODE_ID=? AND FRAGMENT_ID=?");

         psFragment.setInt(1,nodeId);
         psFragment.setInt(2,fragmentId);

         //execute update layout
         count += psFragment.executeUpdate();
         psFragment.close();

      } else {
       PreparedStatement  psLayout =
        con.prepareStatement("DELETE FROM UP_LAYOUT_STRUCT_AGGR WHERE LAYOUT_ID=? AND USER_ID=? AND NODE_ID=?");

         psLayout.setInt(1,layoutId);
         psLayout.setInt(2,userId);
         psLayout.setInt(3,nodeId);

         //execute update layout
         count += psLayout.executeUpdate();
         psLayout.close();
       }


        stmt.close();
        RDBMServices.commit(con);
        con.close();

        return count > 0;

     } catch (Exception e) {
        String errorMessage = e.getMessage();
        try { RDBMServices.rollback(con); } catch ( SQLException sqle ) {
           LogService.log(LogService.ERROR, sqle.toString() );
           errorMessage += ":" + sqle.getMessage();
        }
         throw new PortalException(errorMessage);
       }
    }

   /**
     * Gets the user layout node.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param nodeId a <code>String</code> node ID specifying the node
     * @return a <code>ALNode</code> object
     * @exception PortalException if an error occurs
     */
    public ALNode getUserLayoutNode (IPerson person, UserProfile profile, String nodeId ) throws PortalException {
      return null;
    }

   /**
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @param layoutImpl a <code>IAggregatedLayout</code> containing an aggregated user layout
     * @exception Exception if an error occurs
     */
 public void setAggregatedLayout (IPerson person, UserProfile profile, IAggregatedLayout layoutImpl ) throws Exception {

    if ( !(layoutImpl instanceof AggregatedLayout) )
       throw new PortalException("The user layout object should have \"AggregatedLayout\" type");

    AggregatedLayout layout = (AggregatedLayout) layoutImpl;

    int userId = person.getID();
    int profileId=profile.getProfileId();

    Connection con = RDBMServices.getConnection();

    RDBMServices.setAutoCommit(con, false);       // May speed things up, can't hurt

    try {

       Statement stmt = con.createStatement();

        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profile.getProfileId();
        //LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getUserLayout(): " + subSelectString);
        int layoutId = 0;
        ResultSet rs = stmt.executeQuery(subSelectString);
        try {
            if ( rs.next() )
             layoutId = rs.getInt(1);
        } finally {
            rs.close();
        }


      String sQuery = "SELECT INIT_NODE_ID FROM UP_USER_LAYOUT_AGGR WHERE USER_ID=" + userId + " AND LAYOUT_ID=" + layoutId;
      LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setAggregatedLayout(): " + sQuery);
      String firstNodeId = layout.getLayoutFolder(layout.getRootId()).getFirstChildNodeId();
      rs = stmt.executeQuery(sQuery);
      if ( !rs.next() ) {
         sQuery = "INSERT INTO UP_USER_LAYOUT_AGGR (LAYOUT_ID,USER_ID,LAYOUT_TITLE,INIT_NODE_ID) VALUES ("+layoutId+","+userId+",'"+person.getFullName()+" layout',"
                  +firstNodeId+")";
      } else {
         sQuery = "UPDATE UP_USER_LAYOUT_AGGR SET INIT_NODE_ID="+firstNodeId+" WHERE LAYOUT_ID="+layoutId+" AND USER_ID="+userId;
        }
      LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::setAggregatedLayout(): " + sQuery);
      stmt.executeUpdate(sQuery);
      if ( rs != null ) rs.close();

      // Clear the previous data related to the user layout
      PreparedStatement psDeleteLayout = con.prepareStatement("DELETE FROM UP_LAYOUT_STRUCT_AGGR WHERE USER_ID=? AND LAYOUT_ID=?");
      // Deleting the node from the user layout
      psDeleteLayout.setInt(1,userId);
      psDeleteLayout.setInt(2,layoutId);
      psDeleteLayout.executeUpdate();

      // Deleting restrictions for regular nodes
      PreparedStatement psDeleteLayoutRestriction = con.prepareStatement("DELETE FROM UP_LAYOUT_RESTRICTIONS WHERE USER_ID=? AND LAYOUT_ID=?");
      // Deleting restrictions for the node
      psDeleteLayoutRestriction.setInt(1,userId);
      psDeleteLayoutRestriction.setInt(2,layoutId);
      psDeleteLayoutRestriction.executeUpdate();

       // Deleting restrictions for "pseudo" fragment nodes that exist in the user layout
      //PreparedStatement psDeleteFragmentRestriction = con.prepareStatement("DELETE FROM up_fragment_restrictions WHERE fragment_id=? AND node_id=?");

      // Update prepared statements
      //PreparedStatement  psUpdateFragmentNode = con.prepareStatement(FRAGMENT_UPDATE_SQL);
      PreparedStatement  psUpdateFragmentRestriction = con.prepareStatement(FRAGMENT_RESTRICTION_UPDATE_SQL);
      PreparedStatement  psUpdateLayoutNode = con.prepareStatement(LAYOUT_UPDATE_SQL);
      PreparedStatement  psUpdateLayoutRestriction = con.prepareStatement(LAYOUT_RESTRICTION_UPDATE_SQL);
      PreparedStatement  psUpdateChannelParam = con.prepareStatement(CHANNEL_PARAM_UPDATE_SQL);
      PreparedStatement  psUpdateChannel = con.prepareStatement(CHANNEL_UPDATE_SQL);

      // Add prepared statements
      PreparedStatement  psAddFragmentNode = con.prepareStatement(FRAGMENT_ADD_SQL);
      PreparedStatement  psAddFragmentRestriction = con.prepareStatement(FRAGMENT_RESTRICTION_ADD_SQL);
      PreparedStatement  psAddLayoutNode = con.prepareStatement(LAYOUT_ADD_SQL);
      PreparedStatement  psAddLayoutRestriction = con.prepareStatement(LAYOUT_RESTRICTION_ADD_SQL);
      PreparedStatement  psAddChannelParam = con.prepareStatement(CHANNEL_PARAM_ADD_SQL);
      PreparedStatement  psAddChannel = con.prepareStatement(CHANNEL_ADD_SQL);

      PreparedStatement psLayout = con.prepareStatement("SELECT NODE_ID FROM UP_LAYOUT_STRUCT_AGGR WHERE NODE_ID=? AND USER_ID=? AND LAYOUT_ID=?");
      //PreparedStatement psFragment = con.prepareStatement("SELECT node_id FROM up_layout_struct_aggr WHERE node_id=? AND fragment_id=?");

       // The loop for all the nodes from the layout
       for ( Enumeration nodeIds = layout.getNodeIds(); nodeIds.hasMoreElements() ;) {
        String strNodeId = nodeIds.nextElement().toString();

        if ( !strNodeId.equals(IALFolderDescription.ROOT_FOLDER_ID) && !strNodeId.equals(IALFolderDescription.LOST_FOLDER_ID) ) {

         ALNode node = layout.getNode(strNodeId);
         int nodeId = CommonUtils.parseInt(node.getId());

         int fragmentId = CommonUtils.parseInt(node.getFragmentId());
         int fragmentNodeId = CommonUtils.parseInt(node.getFragmentNodeId());



         if ( fragmentId > 0 && fragmentNodeId <= 0 ) {
           /*ps = psFragment;
           ps.setInt(1,nodeId);
           ps.setInt(2,fragmentId);
           rs = ps.executeQuery();
           if ( rs.next() )
               updateUserLayoutNode(userId,layoutId,node,psUpdateFragmentNode,psUpdateFragmentRestriction,psUpdateChannel,psUpdateChannelParam);
           else
               addUserLayoutNode(userId,layoutId,node,psAddFragmentNode,psAddFragmentRestriction,psAddChannel,psAddChannelParam);
           rs.close();*/
         } else {
             // Setting psLayout parameters
             psLayout.setInt(1,nodeId);
             psLayout.setInt(2,userId);
             psLayout.setInt(3,layoutId);
             rs = psLayout.executeQuery();
             if ( rs.next() )
               updateUserLayoutNode(userId,layoutId,node,psUpdateLayoutNode,(fragmentNodeId>0)?psUpdateFragmentRestriction:psUpdateLayoutRestriction,psUpdateChannel,psUpdateChannelParam);
             else {

              // Deleting "pseudo" fragment node restrictions
              /*if ( fragmentNodeId > 0 && fragmentId > 0 ) {
               psDeleteFragmentRestriction.setInt(1,fragmentId);
               psDeleteFragmentRestriction.setInt(2,fragmentNodeId);
               psDeleteFragmentRestriction.executeUpdate();
              }
              /*else {
                  // Deleting restrictions for the node
                  psDeleteLayoutRestriction.setInt(1,userId);
                  psDeleteLayoutRestriction.setInt(2,layoutId);
                  psDeleteLayoutRestriction.setInt(3,nodeId);
                  psDeleteLayoutRestriction.executeUpdate();
                }*/

               boolean channelParamsExist = false;

               if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                int publishId = CommonUtils.parseInt(((IALChannelDescription)node.getNodeDescription()).getChannelPublishId());
                  ResultSet rsChan = stmt.executeQuery("SELECT CHAN_ID FROM UP_CHANNEL WHERE CHAN_ID=" + publishId);
                  try {
                   if ( rsChan.next() )
                     channelParamsExist = true;
                  } finally {
                     rsChan.close();
                    }
               }

               if ( channelParamsExist )
                 addUserLayoutNode(userId,layoutId,node,psAddLayoutNode,(fragmentNodeId>0)?psAddFragmentRestriction:psAddLayoutRestriction,null,null);
               else
                 addUserLayoutNode(userId,layoutId,node,psAddLayoutNode,(fragmentNodeId>0)?psAddFragmentRestriction:psAddLayoutRestriction,psAddChannel,psAddChannelParam);
            }

             rs.close();
           }
         } // End if
        } // End for


      if ( stmt != null ) stmt.close();

      // Commit all the changes
      RDBMServices.commit(con);

      //if ( psFragment != null ) psFragment.close();
      if ( psLayout != null ) psLayout.close();

      //if ( psUpdateFragmentNode != null ) psUpdateFragmentNode.close();
      //if ( psUpdateFragmentRestriction != null ) psUpdateFragmentRestriction.close();
      if ( psUpdateLayoutNode != null ) psUpdateLayoutNode.close();
      if ( psUpdateLayoutRestriction != null ) psUpdateLayoutRestriction.close();

      if ( psDeleteLayout != null ) psDeleteLayout.close();
      if ( psDeleteLayoutRestriction != null ) psDeleteLayoutRestriction.close();

      //if ( psAddFragmentNode != null ) psAddFragmentNode.close();
      //if ( psAddFragmentRestriction != null ) psAddFragmentRestriction.close();
      //if ( psDeleteFragmentRestriction != null ) psDeleteFragmentRestriction.close();
      if ( psAddLayoutNode != null ) psAddLayoutNode.close();
      if ( psAddLayoutRestriction != null ) psAddLayoutRestriction.close();

      if ( psUpdateChannel != null ) psUpdateChannel.close();
      if ( psUpdateChannelParam != null ) psUpdateChannelParam.close();

      // Close the connection
      con.close();


    } catch (Exception e) {
        e.printStackTrace();
        String errorMessage = e.getMessage();
        try { RDBMServices.rollback(con); } catch ( SQLException sqle ) {
           LogService.log(LogService.ERROR, sqle.toString() );
           errorMessage += ":" + sqle.getMessage();
        }
         throw new PortalException(errorMessage);
      }
 }


 /**   Gets the fragment IDs/fragment descriptions for a given user
     * @param person an <code>IPerson</code> object specifying the user
     * @return a <code>Map</code> object containing the IDs of the fragments the user owns
     * @exception Exception if an error occurs
     */
 public Map getFragments (IPerson person) throws Exception {

    Connection con = RDBMServices.getConnection();

    Map fragments = new Hashtable();
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT FRAGMENT_ID, FRAGMENT_DESCRIPTION FROM UP_OWNER_FRAGMENT WHERE OWNER_ID="+person.getID());
    while ( rs.next() )
      fragments.put ( rs.getInt(1) + "", rs.getString(2) );

    if ( rs != null ) rs.close();
    if ( stmt != null ) stmt.close();
    if ( con != null ) con.close();

    return fragments;
 }


 /**   Sets the fragment
     * @param person an <code>IPerson</code> object specifying the user
     * @param fragment a <code>ILayoutFragment</code> containing a fragment
     * @exception Exception if an error occurs
     */
 public void setFragment (IPerson person, ILayoutFragment fragment ) throws Exception {

    int userId = person.getID();
    String fragmentId = fragment.getId();
    Connection con = RDBMServices.getConnection();

     if ( !(fragment instanceof ALFragment) )
       throw new PortalException("The user layout fragment must have "+ALFragment.class.getName()+" type!");

    ALFragment layout = (ALFragment) fragment;
  
    RDBMServices.setAutoCommit(con, false);       // May speed things up, can't hurt

    try {

       Statement stmt = con.createStatement();
       
	     boolean isOwner = false;
	     boolean isNewFragment = false;
		 // Check if the user was an owner
		 ResultSet rs = stmt.executeQuery("SELECT OWNER_ID FROM UP_OWNER_FRAGMENT WHERE FRAGMENT_ID="+fragmentId);
		 if ( rs.next() ) {
		  if ( rs.getInt(1) == userId )
		    isOwner = true;
		 } else
		    isNewFragment = true;  
		 if ( rs != null ) rs.close();
		 
		 if ( !isOwner && !isNewFragment )
		  throw new PortalException("The user "+userId+" is not an owner of the fragment"+fragmentId);

      // Check if the fragment is new
      if ( isNewFragment ) {
      	ALFolder rootNode = layout.getLayoutFolder(layout.getRootId());
      	String fragmentRootId = rootNode.getFirstChildNodeId();
		String sqlInsert = "INSERT INTO UP_OWNER_FRAGMENT (FRAGMENT_ID,FRAGMENT_ROOT_ID,OWNER_ID,FRAGMENT_NAME,FRAGMENT_DESCRIPTION,PUSHED_FRAGMENT) "+	
		"VALUES (?,?,?,?,?,?)";
		PreparedStatement ps = con.prepareStatement(sqlInsert);
		ps.setInt(1,CommonUtils.parseInt(fragmentId));
		if ( fragmentRootId != null )
		 ps.setInt(2,CommonUtils.parseInt(fragmentRootId));
		else
		 ps.setNull(2,Types.INTEGER); 
		ps.setInt(3,userId);
		ps.setString(4,layout.getName());
		ps.setString(5,layout.getDescription());
		ps.setString(6,(layout.isPushedFragment())?"Y":"N");
     	ps.executeUpdate();
      } else {
		 	 
		 String sqlUpdate = "UPDATE UP_OWNER_FRAGMENT SET FRAGMENT_NAME=?,FRAGMENT_DESCRIPTION=?,PUSHED_FRAGMENT=? WHERE OWNER_ID=? AND FRAGMENT_ID=?";	
		 PreparedStatement ps = con.prepareStatement(sqlUpdate);
		 ps.setString(1,layout.getName());
		 ps.setString(2,layout.getDescription());
		 ps.setString(3,(layout.isPushedFragment())?"Y":"N");
		 ps.setInt(4,userId);
		 ps.setInt(5,CommonUtils.parseInt(fragmentId));
		 ps.executeUpdate();
		 
        }  

      // Clear the previous data related to the user layout
      stmt.executeUpdate("DELETE FROM UP_FRAGMENTS WHERE FRAGMENT_ID="+fragmentId);

      // Deleting restrictions for fragment nodes
      stmt.executeUpdate("DELETE FROM UP_FRAGMENT_RESTRICTIONS WHERE FRAGMENT_ID="+fragmentId);

      // Add prepared statements
      PreparedStatement  psAddFragmentNode = con.prepareStatement(FRAGMENT_ADD_SQL);
      PreparedStatement  psAddFragmentRestriction = con.prepareStatement(FRAGMENT_RESTRICTION_ADD_SQL);

       // The loop for all the nodes from the layout
      for ( Enumeration nodeIds = layout.getNodeIds(); nodeIds.hasMoreElements() ;) {
        String strNodeId = nodeIds.nextElement().toString();

       if ( !strNodeId.equals(IALFolderDescription.ROOT_FOLDER_ID) && !strNodeId.equals(IALFolderDescription.LOST_FOLDER_ID) ) {

         ALNode node = layout.getNode(strNodeId);
         int nodeId = CommonUtils.parseInt(node.getId());
         
         // Setting the fragment ID
         node.getNodeDescription().setFragmentId(fragmentId);

         int fragmentNodeId = CommonUtils.parseInt(node.getFragmentNodeId());
        
         if (  CommonUtils.parseInt(node.getFragmentId()) > 0 && fragmentNodeId <= 0 )
           addUserLayoutNode(userId,0,node,psAddFragmentNode,psAddFragmentRestriction,null,null);

       } // End if
      } // End for


      if ( stmt != null ) stmt.close();

      // Commit all the changes
      RDBMServices.commit(con);



      if ( psAddFragmentNode != null ) psAddFragmentNode.close();
      if ( psAddFragmentRestriction != null ) psAddFragmentRestriction.close();

      // Close the connection
      con.close();


    } catch (Exception e) {
        e.printStackTrace();
        String errorMessage = e.getMessage();
        try { RDBMServices.rollback(con); } catch ( SQLException sqle ) {
           LogService.log(LogService.ERROR, sqle.toString() );
           errorMessage += ":" + sqle.getMessage();
        }
         throw new PortalException(errorMessage);
      }
 }




    /**
     * Returns the user layout internal representation.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @return a <code>IAggregatedLayout</code> object containing the internal representation of the user layout
     * @exception PortalException if an error occurs
     */
 public IAggregatedLayout getAggregatedLayout (IPerson person, UserProfile profile) throws PortalException {
    int userId = person.getID();
    int realUserId = userId;
    ResultSet rs;

    Connection con = null;
    AggregatedLayout layout = null;
    Hashtable layoutData = null;
    ALFolder rootNode = new ALFolder();
    //PreparedStatement psRestrLayout = null, psRestrFragment = null;
    Hashtable pushFragmentRoots = null;
    String pushFragmentIds = null;

  try {

       EntityIdentifier personIdentifier = person.getEntityIdentifier();
       IGroupMember groupPerson = GroupService.getGroupMember(personIdentifier);


        con = RDBMServices.getConnection();
        RDBMServices.setAutoCommit(con,false);

        layoutData = new Hashtable(50);


       Iterator containingGroups = groupPerson.getAllContainingGroups();

       if ( containingGroups.hasNext() ) {
        //Connection extraCon = RDBMServices.getConnection();

        // Getting push-fragments based on a group key parameter
        PreparedStatement psGroups = con.prepareStatement("SELECT UOF.FRAGMENT_ID, UOF.FRAGMENT_ROOT_ID FROM UP_GROUP_FRAGMENT UPG, UP_OWNER_FRAGMENT UOF " +
                                                          "WHERE UPG.GROUP_KEY=? AND UPG.FRAGMENT_ID = UOF.FRAGMENT_ID AND UOF.PUSHED_FRAGMENT='Y'");

        pushFragmentRoots = new Hashtable();
        while ( containingGroups.hasNext() ) {
          IEntityGroup entityGroup = (IEntityGroup) containingGroups.next();
          psGroups.setString(1,entityGroup.getKey());
          ResultSet rsGroups = psGroups.executeQuery();
          if ( rsGroups.next() ) {
           int fragmentId = rsGroups.getInt(1);
           if ( pushFragmentIds == null )
             pushFragmentIds = fragmentId+"";
           else
            pushFragmentIds += "," + fragmentId;
           pushFragmentRoots.put(""+fragmentId,rsGroups.getInt(2)+"");
          }
          while ( rsGroups.next() ) {
           int fragmentId = rsGroups.getInt(1);
           pushFragmentIds += "," + fragmentId;
           pushFragmentRoots.put(""+fragmentId,rsGroups.getInt(2)+"");
          }
          if ( rsGroups != null ) rsGroups.close();
        }

         if ( psGroups != null ) psGroups.close();
          //RDBMServices.releaseConnection(extraCon);
       } // end if hasNext()

        Statement stmt = con.createStatement();
        // A separate statement is needed so as not to interfere with ResultSet
        // of statements used for queries
        Statement insertStmt = con.createStatement();


      try {
        long startTime = System.currentTimeMillis();
        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" + profile.getProfileId();
        LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + subSelectString);
        int layoutId = 0;
        rs = stmt.executeQuery(subSelectString);
        try {
            if ( rs.next() )
             layoutId = rs.getInt(1);
        } finally {
            rs.close();
        }

       if (layoutId == 0) { // First time, grab the default layout for this user
          String sQuery = "SELECT USER_DFLT_USR_ID, USER_DFLT_LAY_ID FROM UP_USER WHERE USER_ID=" + userId;
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + sQuery);
          rs = stmt.executeQuery(sQuery);
          try {
            if ( rs.next() ) {
             userId = rs.getInt(1);
             layoutId = rs.getInt(2);
            }
          } finally {
            rs.close();
          }

          // Make sure the next struct id is set in case the user adds a channel
          sQuery = "SELECT NEXT_STRUCT_ID FROM UP_USER WHERE USER_ID=" + userId;
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + sQuery);
          int nextStructId = 0;
          rs = stmt.executeQuery(sQuery);
          try {
            if ( rs.next() )
             nextStructId = rs.getInt(1);
          } finally {
            rs.close();
          }
          sQuery = "UPDATE UP_USER SET NEXT_STRUCT_ID=" + nextStructId + " WHERE USER_ID=" + realUserId;
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + sQuery);
          stmt.executeUpdate(sQuery);

          sQuery = "DELETE FROM UP_SS_USER_ATTS WHERE USER_ID=" + realUserId;
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + sQuery);
          stmt.executeUpdate(sQuery);

          // modifed INSERT INTO SELECT statement for MySQL support
          sQuery = " SELECT "+realUserId+", PROFILE_ID, SS_ID, SS_TYPE, STRUCT_ID, PARAM_NAME, PARAM_TYPE, PARAM_VAL "+
            " FROM UP_SS_USER_ATTS WHERE USER_ID="+userId;
          rs = stmt.executeQuery(sQuery);



          while (rs.next()) {
             String Insert = "INSERT INTO UP_SS_USER_ATTS (USER_ID, PROFILE_ID, SS_ID, SS_TYPE, STRUCT_ID, PARAM_NAME, PARAM_TYPE, PARAM_VAL) " +
             "VALUES("+realUserId+","+
              rs.getInt("PROFILE_ID")+","+
              rs.getInt("SS_ID")+"," +
              rs.getInt("SS_TYPE")+"," +
              rs.getString("STRUCT_ID")+"," +
              "'"+rs.getString("PARAM_NAME")+"'," +
              rs.getInt("PARAM_TYPE")+"," +
              "'"+rs.getString("PARAM_VAL")+"')";
// old code
//          String Insert = "INSERT INTO UP_SS_USER_ATTS (USER_ID, PROFILE_ID, SS_ID, SS_TYPE, STRUCT_ID, PARAM_NAME, PARAM_TYPE, PARAM_VAL) "+
//            " SELECT "+realUserId+", USUA.PROFILE_ID, USUA.SS_ID, USUA.SS_TYPE, USUA.STRUCT_ID, USUA.PARAM_NAME, USUA.PARAM_TYPE, USUA.PARAM_VAL "+
//            " FROM UP_SS_USER_ATTS USUA WHERE USUA.USER_ID="+userId;

          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + Insert);
          insertStmt.executeUpdate(Insert);
         }

          // Close Result Set
          if ( rs != null ) rs.close();

          RDBMServices.commit(con); // Make sure it appears in the store
        } // end if layoutID == null

        // we have to delete all the records from up_layout_struct_aggr table related to the lost nodes
        stmt.executeUpdate("DELETE FROM UP_LAYOUT_STRUCT_AGGR WHERE USER_ID="+userId+" AND LAYOUT_ID="+layoutId+" AND PRNT_NODE_ID="+LOST_FOLDER_ID);

        // Instantiating the layout and setting the layout ID
        layout = new AggregatedLayout ( layoutId + "" );

        String restrLayoutSQL = "SELECT RESTRICTION_TYPE, RESTRICTION_VALUE, RESTRICTION_TREE_PATH FROM UP_LAYOUT_RESTRICTIONS "+
                                      "WHERE LAYOUT_ID="+layoutId+" AND USER_ID="+userId+" AND NODE_ID=?";
        String restrFragmentSQL = "SELECT RESTRICTION_TYPE, RESTRICTION_VALUE, RESTRICTION_TREE_PATH FROM UP_FRAGMENT_RESTRICTIONS "+
                                      "WHERE FRAGMENT_ID=? AND NODE_ID=?";

        int firstStructId = -1;
        String sQuery = "SELECT INIT_NODE_ID FROM UP_USER_LAYOUT_AGGR WHERE USER_ID=" + userId + " AND LAYOUT_ID = " + layoutId;
        LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + sQuery);
        rs = stmt.executeQuery(sQuery);
        try {
          if ( rs.next() )
            firstStructId = rs.getInt(1);
          else
            throw new PortalException("AggregatedUserLayoutStore::getAggregatedLayout(): No INIT_NODE_ID in UP_USER_LAYOUT_AGGR for " + userId + " and LAYOUT_ID " + layoutId);
        } finally {
          rs.close();
        }

        // Creating a root folder
        rootNode = ALFolder.createRootFolder();
        // Setting the first layout node ID to the root folder
        rootNode.setFirstChildNodeId(firstStructId+"");

        // Putting the root node
        layoutData.put(IALFolderDescription.ROOT_FOLDER_ID,rootNode);
         // Putting the lost folder
        layoutData.put(IALFolderDescription.LOST_FOLDER_ID,ALFolder.createLostFolder());

        // layout query
        String sqlLayout = "SELECT ULS.NODE_ID,ULS.NEXT_NODE_ID,ULS.CHLD_NODE_ID,ULS.PREV_NODE_ID,ULS.PRNT_NODE_ID,ULS.CHAN_ID,ULS.NAME,ULS.TYPE,ULS.HIDDEN,"+
          "ULS.UNREMOVABLE,ULS.IMMUTABLE,ULS.PRIORITY,ULS.FRAGMENT_ID,ULS.FRAGMENT_NODE_ID";
        if (RDBMServices.supportsOuterJoins) {
          sqlLayout += ",USP.STRUCT_PARM_NM,USP.STRUCT_PARM_VAL FROM " + RDBMServices.joinQuery.getQuery("layout_aggr");
        } else {
          sqlLayout += " FROM UP_LAYOUT_STRUCT_AGGR ULS WHERE ";
        }
        sqlLayout += " ULS.USER_ID="+userId+" AND ULS.LAYOUT_ID="+layoutId;

        LogService.log(LogService.DEBUG, sqlLayout);

        // The query for getting information of the fragments
        String sqlFragment = "SELECT DISTINCT UF.NODE_ID,UF.NEXT_NODE_ID,UF.CHLD_NODE_ID,UF.PREV_NODE_ID,UF.PRNT_NODE_ID,UF.CHAN_ID,UF.NAME,UF.TYPE,UF.HIDDEN,"+
          "UF.UNREMOVABLE,UF.IMMUTABLE,UF.PRIORITY,UF.FRAGMENT_ID";
        if (RDBMServices.supportsOuterJoins) {
          sqlFragment += ",UFP.PARAM_NAME,UFP.PARAM_VALUE FROM UP_LAYOUT_STRUCT_AGGR ULS, " + fragmentJoinQuery;
        } else {
          sqlFragment += " FROM UP_FRAGMENTS UF, UP_LAYOUT_STRUCT_AGGR ULS WHERE ";
        }
        sqlFragment += " ULS.FRAGMENT_ID=UF.FRAGMENT_ID" + ((pushFragmentIds!=null)?" OR UF.FRAGMENT_ID IN ("+pushFragmentIds+")":"");

        LogService.log(LogService.DEBUG, sqlFragment);

        // The hashtable object containing the fragment nodes that are next to the user layout nodes
        Hashtable fragmentNodes = new Hashtable();

        int count = 0;
        for ( String sql = sqlLayout; count < 2; sql = sqlFragment, count++ ) {

         List chanIds = Collections.synchronizedList(new ArrayList());
         StringBuffer structParms = new StringBuffer();



         rs = stmt.executeQuery(sql);

         try {
          int lastStructId = 0;
          String sepChar = "";
          if (rs.next()) {
            int structId = rs.getInt(1);
            /*if (rs.wasNull()) {
              structId = 0;
            }*/


            readLayout: while (true) {

              if (DEBUG > 1) System.err.println("Found layout structureID " + structId);


              int nextId = rs.getInt(2);
              /*if (rs.wasNull()) {
                nextId = 0;
              }*/


              int childId = rs.getInt(3);
              /*if (rs.wasNull()) {
                childId = 0;
              }*/

              int prevId = rs.getInt(4);
              /*if (rs.wasNull()) {
                prevId = 0;
              }*/
              int prntId = rs.getInt(5);
              /*if (rs.wasNull()) {
                prntId = 0;
              }*/
              int chanId = rs.getInt(6);
              /*if (rs.wasNull()) {
                chanId = 0;
              }*/

              int fragmentId = rs.getInt(13);
              int fragmentNodeId = ( sql.equals(sqlLayout) )?rs.getInt(14):0;

              IALNodeDescription nodeDesc= null;
              // Trying to get the node if it already exists
              //ALNode node = (ALNode) layout.get(structId+"");
              ALNode node;
              String childIdStr = null;
              if ( ( chanId <= 0 && fragmentId <= 0 ) || ( fragmentId > 0 && ( childId > 0 || fragmentNodeId > 0 ) ) ) {
                //if ( node == null )
                node = new ALFolder();
                IALFolderDescription folderDesc = new ALFolderDescription();
                // If children exist in the folder
                if ( childId > 0 )
                 childIdStr = ( fragmentId > 0 && fragmentNodeId <= 0 )?(fragmentId+":"+childId):(childId+"");
                ((ALFolder)node).setFirstChildNodeId(childIdStr);
                String type = rs.getString(8);
                int intType;
                if ( "header".equalsIgnoreCase(type))
                 intType = UserLayoutFolderDescription.HEADER_TYPE;
                else if ( "footer".equalsIgnoreCase(type))
                 intType = UserLayoutFolderDescription.FOOTER_TYPE;
                else
                 intType = UserLayoutFolderDescription.REGULAR_TYPE;

                folderDesc.setFolderType(intType);
                nodeDesc = folderDesc;
              } else {
                 //if ( node == null )
                 node = new ALChannel();
                 ALChannelDescription channelDesc = new ALChannelDescription();
                 channelDesc.setChannelPublishId(rs.getString(6));
                 nodeDesc = channelDesc;
                }

              // Setting node description attributes
              if ( node.getNodeType() == IUserLayoutNodeDescription.FOLDER )
                 nodeDesc.setName(rs.getString(7));
              nodeDesc.setHidden(("Y".equalsIgnoreCase(rs.getString(9))?true:false));
              nodeDesc.setImmutable(("Y".equalsIgnoreCase(rs.getString(11))?true:false));
              nodeDesc.setUnremovable(("Y".equalsIgnoreCase(rs.getString(10))?true:false));
              node.setPriority(rs.getInt(12));


              nodeDesc.setFragmentId((fragmentId>0)?fragmentId+"":null);

              if ( sql.equals(sqlLayout) ) {
               nodeDesc.setFragmentNodeId((fragmentNodeId>0)?fragmentNodeId+"":null);
              }

              // Setting the node id
              if ( fragmentId > 0 && fragmentNodeId <= 0 )
               nodeDesc.setId(fragmentId+":"+structId);
              else
               nodeDesc.setId((structId!=LOST_FOLDER_ID)?(structId+""):IALFolderDescription.LOST_FOLDER_ID);

              // Setting the next node id
              if ( nextId != 0 ) {
                 //node.setNextNodeId((nextId!=LOST_NODE_ID)?(nextId+""):IALFolderDescription.LOST_FOLDER_ID);
               String nextIdStr = ( fragmentId > 0 && fragmentNodeId <= 0 )?(fragmentId+":"+nextId):(nextId+"");
               node.setNextNodeId(nextIdStr);
              }

              String parentId;
              switch ( prntId ) {
               case 0:
                               parentId = IALFolderDescription.ROOT_FOLDER_ID;
                               break;
               case LOST_FOLDER_ID:
                               parentId = IALFolderDescription.LOST_FOLDER_ID;
                               break;
               default:
                               parentId = ( fragmentId > 0 && fragmentNodeId <= 0 )?(fragmentId+":"+prntId):(prntId+"");

              }

              // Setting up the parent id
              node.setParentNodeId(parentId);

              // Setting the previous node id
              if ( prevId != 0 ) {
                //node.setPreviousNodeId((prevId!=LOST_NODE_ID)?(prevId+""):IALFolderDescription.LOST_FOLDER_ID);
               String prevIdStr = ( fragmentId > 0 && fragmentNodeId <= 0 )?(fragmentId+":"+prevId):(prevId+"");
               node.setPreviousNodeId(prevIdStr);
              }

              lastStructId = structId;


            String fragmentNodeIdStr = nodeDesc.getFragmentNodeId();
            String fragmentIdStr = nodeDesc.getFragmentId();
            String nodeIdStr = structId+"";
            String key = fragmentId+":"+structId;

              // Putting the node into the layout hashtable with an appropriate key
              node.setNodeDescription(nodeDesc);
              if ( fragmentNodeIdStr != null ) {
               fragmentNodes.put(fragmentIdStr+":"+fragmentNodeIdStr,node);
              } else {
                  if ( fragmentIdStr != null && fragmentNodes.containsKey(key) ) {
                    ALNode fragNode = (ALNode) fragmentNodes.get(key);
                    //Setting the actual node ID
                    nodeDesc.setId(fragNode.getId());
                    nodeDesc.setFragmentNodeId(fragNode.getFragmentNodeId());
                    fragNode.setNodeDescription(nodeDesc);
                    if ( fragNode.getNodeType() == IUserLayoutNodeDescription.FOLDER ) {
                     ((ALFolder)fragNode).setFirstChildNodeId(childIdStr);
                    }
                    layoutData.put(nodeDesc.getId(),fragNode);
                  } else
                      layoutData.put(nodeDesc.getId(),node);
                }

              // If there is a channel we need to get its parameters
              IALChannelDescription channelDesc = null;
              if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                channelDesc = (IALChannelDescription) nodeDesc;
                chanIds.add(nodeDesc.getId());
              }

              // getting restrictions for the nodes
              PreparedStatement psRestr = null;
              if ( sql.equals(sqlLayout) && fragmentNodeId <= 0) {
                  psRestr = con.prepareStatement(restrLayoutSQL);
                  psRestr.setInt(1,structId);
              } else {
                  psRestr = con.prepareStatement(restrFragmentSQL);
                  psRestr.setInt(1,fragmentId);
                  psRestr.setInt(2,(fragmentNodeId>0)?fragmentNodeId:structId);
              }
              ResultSet rsRestr = psRestr.executeQuery();
              while (rsRestr.next()) {
                  int restrType = rsRestr.getInt(1);
                  String restrExp = rsRestr.getString(2);
                  String restrPath = rsRestr.getString(3);
                  if ( restrPath == null || restrPath.trim().length() == 0 )
                    restrPath = UserLayoutRestriction.LOCAL_RESTRICTION;
                  IUserLayoutRestriction restriction = UserLayoutRestrictionFactory.createRestriction(restrType,restrExp,restrPath);
                  nodeDesc.addRestriction(restriction);
              }
               rsRestr.close();
               if ( psRestr != null ) psRestr.close();

              int index = (sql.equals(sqlLayout))?15:14;

              if (RDBMServices.supportsOuterJoins) {
                do {
                  String name = rs.getString(index);
                  String value = rs.getString(index+1); // Oracle JDBC requires us to do this for longs
                  if (name != null) { // may not be there because of the join
                      if ( channelDesc != null )
                          channelDesc.setParameterValue(name,value);
                  }


                  if (!rs.next()) {
                    break readLayout;
                  }
                  structId = rs.getInt(1);
                  if (rs.wasNull()) {
                    structId = 0;
                  }
                } while (structId == lastStructId);
              } else { // Do second SELECT later on for structure parameters

                  // Adding the channel ID to the String buffer
                  if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                   structParms.append(sepChar + chanId);
                   sepChar = ",";
                  }

                 if (rs.next()) {
                  structId = rs.getInt(1);
                  if (rs.wasNull()) {
                    structId = 0;
                  }
                 } else {
                    break readLayout;
                   }
                } //end else

                // Setting up the priority values based on the appropriate priority restrictions
                PriorityRestriction priorityRestriction = AggregatedLayoutManager.getPriorityRestriction(node);
                if ( priorityRestriction != null ) {
                 int priority = node.getPriority();
                 int[] range = priorityRestriction.getRange();

                 int newPriority = priority;
                 if ( range[0] > priority )
                     newPriority = range[0];
                 else if ( range[1] < priority )
                     newPriority = range[1];

                 // Changing the node priority if it's been changed
                 if ( newPriority != priority )
                     node.setPriority(newPriority);
                }


            } // while

            /*
             if ( psRestrLayout != null ) psRestrLayout.close();
             if ( psRestrFragment != null ) psRestrFragment.close();
            */
          }
        } finally {
          rs.close();
        }

        // We have to retrieve the channel defition after the layout structure
        // since retrieving the channel data from the DB may interfere with the
        // layout structure ResultSet (in other words, Oracle is a pain to program for)
        if (chanIds.size() > 0) {

              for (int i = 0; i < chanIds.size(); i++) {

                String key = (String) chanIds.get(i);

                ALNode node = (ALNode) layoutData.get(key);

                fillChannelDescription( (IALChannelDescription) node.getNodeDescription() );

              }

            chanIds.clear();
        }

        if ( !RDBMServices.supportsOuterJoins && structParms.length() > 0 ) { // Pick up structure parameters
          String paramSql = "SELECT STRUCT_ID, STRUCT_PARM_NM,STRUCT_PARM_VAL FROM UP_LAYOUT_PARAM WHERE USER_ID=" + userId + " AND LAYOUT_ID=" + layoutId +
            " AND STRUCT_ID IN (" + structParms.toString() + ") ORDER BY STRUCT_ID";
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): " + paramSql);

          // Adding this to prevent the error "closed statement" in Oracle
          Statement st = con.createStatement();

          rs = st.executeQuery(paramSql);

          try {
            if (rs.next()) {


              int structId = rs.getInt(1);
              readParm: while(true) {


                //LayoutStructure ls = (LayoutStructure)layoutStructure.get(new Integer(structId));
                ALNode node = (ALNode) layoutData.get(structId+"");
                if ( node != null ) {
                 IALChannelDescription channelDesc = (IALChannelDescription) node.getNodeDescription();
                 int lastStructId = structId;
                 do {

                   //ls.addParameter(rs.getString(2), rs.getString(3));
                   String name = rs.getString(2);
                   String value = rs.getString(3);
                   channelDesc.setParameterValue(name,value);
                   if (!rs.next()) {
                     break readParm;
                   }
                 } while ((structId = rs.getInt(1)) == lastStructId);

                } else break readParm; // if else
              }
            }
          } finally {
            rs.close();
            st.close();
          }
        }

       } // End of for


       // Very suspicious place !!!!
       // Check if the node from an user layout points to a fragment node, we have to bind them
       // The loop for all the nodes from the hashtable
       //System.out.println( "Getting layout size: " + layout.size() );
       /*for ( Enumeration nodeIds = layout.keys(); nodeIds.hasMoreElements() ;) {
          String strNodeId = nodeIds.nextElement().toString();
          //System.out.println( "Getting nodeId: " + strNodeId );
          ALNode node = (ALNode) layout.get(strNodeId);
           if ( node.getFragmentId() == null ) {

                  String nextNodeId = node.getNextNodeId();
                  ALNode nextNode = null;
                  if ( nextNodeId != null ) nextNode =  (ALNode) layout.get(nextNodeId);

                  String prevNodeId = node.getPreviousNodeId();
                  ALNode prevNode = null;
                  if ( prevNodeId != null ) prevNode =  (ALNode) layout.get(prevNodeId);

                  String prntNodeId = node.getParentNodeId();
                  ALNode prntNode = null;
                  if ( prntNodeId != null ) prntNode =  (ALNode) layout.get(prntNodeId);

                  String firstChildId = ("folder".equals(node.getNodeType()))?((ALFolder)node).getFirstChildNodeId():null;
                  ALNode childNode = null;
                  if ( firstChildId != null )
                   childNode = (ALNode)layout.get(firstChildId);

                  if ( nextNode != null && nextNode.getFragmentId() != null ) nextNode.setPreviousNodeId(strNodeId);
                  if ( prevNode != null && prevNode.getFragmentId() != null ) prevNode.setNextNodeId(strNodeId);
                  // if we have the following: node = the first child of his parent what is a fragment node :))
                  if ( prntNode != null && prntNode.getFragmentId() != null && node.getPreviousNodeId() == null )
                     if ( "folder".equals(prntNode.getNodeType()) )
                        ((ALFolder)prntNode).setFirstChildNodeId(strNodeId);
                  // Checking all the children
                  if ( childNode != null ) {
                   for ( String tmpNodeId = childNode.getId(); tmpNodeId != null; ) {
                    ALNode tmpChildNode =  (ALNode) layout.get(tmpNodeId);
                    // if we got tmpChildNode == NULL we have to get out of the loop
                    //if ( tmpChildNode != null ) {
                     if ( tmpChildNode.getFragmentId() != null )
                       tmpChildNode.setParentNodeId(strNodeId);
                     tmpNodeId = tmpChildNode.getNextNodeId();
                    //} else break;
                   }
                  }
            }
       }
         */


        // finding the last node in the sibling line of the root children
        ALNode lastNode = null, prevNode = null;
        String nextId = rootNode.getFirstChildNodeId();
        while ( nextId != null ) {
          lastNode = (ALNode)layoutData.get(nextId);
          // If neccessary cleaning the end of tabs sibling line setting the next ID to null of the last tab
          if ( lastNode == null ) {
           if ( prevNode != null ) {
             prevNode.setNextNodeId(null);
             lastNode = prevNode;
           }
             break;
          }
          nextId = lastNode.getNextNodeId();
          prevNode = lastNode;
        }

        // Binding the push-fragments to the end of the sibling line of the root children
        if ( pushFragmentRoots != null ) {
         for ( Enumeration fragmentIds = pushFragmentRoots.keys(); fragmentIds.hasMoreElements() ;) {
            String strFragmentId = fragmentIds.nextElement().toString();
            String strFragmentRootId = pushFragmentRoots.get(strFragmentId).toString();
            String key = strFragmentId+":"+strFragmentRootId;
            ALNode node = (ALNode) layoutData.get(key);
            if ( node != null ) {
                IALNodeDescription nodeDesc = node.getNodeDescription();
                // Setting the new next struct node ID and fragment node id since we have all the pushed fragments attached to the layout
                String newId = getNextStructId(person,"");
                nodeDesc.setId(newId);
                nodeDesc.setFragmentNodeId(strFragmentRootId);
                // Remove the old node and put the new one with another ID
                layoutData.remove(key);
                layoutData.put(newId,node);
                if ( lastNode != null ) {
                    lastNode.setNextNodeId(newId);
                    node.setPreviousNodeId(lastNode.getId());
                } else
                    rootNode.setFirstChildNodeId(newId);

                if ( node.getNodeType() == IUserLayoutNodeDescription.FOLDER ) {
                    //Changing the parent Ids for all the children
                    for ( String nextIdStr = ((ALFolder)node).getFirstChildNodeId(); nextIdStr != null; ) {
                        ALNode child = (ALNode) layoutData.get(nextIdStr);
                        child.setParentNodeId(newId);
                        nextIdStr = child.getNextNodeId();
                    }
                }

                node.setParentNodeId(IALFolderDescription.ROOT_FOLDER_ID);
                lastNode = node;
            }
        } // end for
       } // end if

        for ( Enumeration fragmentNodesEnum = fragmentNodes.keys(); fragmentNodesEnum.hasMoreElements() ;) {
               String key = fragmentNodesEnum.nextElement().toString();
               ALNode node  = (ALNode ) fragmentNodes.get(key);
               if ( node.getNodeType() == IUserLayoutNodeDescription.FOLDER ) {
                   String parentId = node.getId();
                 for ( String nextIdStr = ((ALFolder)node).getFirstChildNodeId(); nextIdStr != null; ) {
                     ALNode child = (ALNode) layoutData.get(nextIdStr);
                     child.setParentNodeId(parentId);
                     nextIdStr = child.getNextNodeId();
                 }
               }
        }

          long stopTime = System.currentTimeMillis();
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getUserLayout(): Layout document for user " + userId + " took " +
            (stopTime - startTime) + " milliseconds to create");

      } finally {
        if ( insertStmt != null ) insertStmt.close();
        if ( stmt != null ) stmt.close();
      }
    } catch ( Exception e ) {
         e.printStackTrace();
         LogService.log(LogService.ERROR,e);
         throw new PortalException(e);
      } finally {
          RDBMServices.releaseConnection(con);
    }

           layout.setLayoutData(layoutData);
           return layout;
  }


  /**
     * Returns the layout fragment as a user layout
     * @param person an <code>IPerson</code> object specifying the user
     * @param fragmentIdStr a fragment ID
     * @return a <code>IAggregatedLayout</code> object containing the internal representation of the user layout
     * @exception PortalException if an error occurs
     */
 public ILayoutFragment getFragment (IPerson person, String fragmentIdStr ) throws PortalException {
    int userId = person.getID();
    int fragmentId = CommonUtils.parseInt(fragmentIdStr);
    int realUserId = userId;
    ResultSet rs;

    ALFragment layout = new ALFragment ( fragmentIdStr );

    Connection con = null;
    Hashtable layoutData = null;
    ALFolder rootNode = new ALFolder();

  try {

        con = RDBMServices.getConnection();
        RDBMServices.setAutoCommit(con,false);

        Statement stmt = con.createStatement();

        layoutData = new Hashtable();

        long startTime = System.currentTimeMillis();
        // eventually, we need to fix template layout implementations so you can just do this:
        //        int layoutId=profile.getLayoutId();
        // but for now:

        String restrFragmentSQL = "SELECT RESTRICTION_TYPE, RESTRICTION_VALUE, RESTRICTION_TREE_PATH FROM UP_FRAGMENT_RESTRICTIONS "+
                                      "WHERE FRAGMENT_ID=? AND NODE_ID=?";

        int firstStructId = -1;
        String sQuery = "SELECT FRAGMENT_ROOT_ID,FRAGMENT_NAME,FRAGMENT_DESCRIPTION,PUSHED_FRAGMENT FROM UP_OWNER_FRAGMENT WHERE FRAGMENT_ID=" + fragmentId + " AND OWNER_ID = " + userId;
        rs = stmt.executeQuery(sQuery);
        try {
          rs.next();
          firstStructId = rs.getInt(1);
          layout.setName(rs.getString(2));
		  layout.setDescription(rs.getString(3));
		  if ("Y".equals(rs.getString(4))) 
		     layout.setPushedFragment();
		  else   
		     layout.setPulledFragment();
        } finally {
          rs.close();
        }

        // Creating a root folder
        rootNode = ALFolder.createRootFolder();

        // Putting the root node
        layoutData.put(IALFolderDescription.ROOT_FOLDER_ID,rootNode);
         // Putting the lost folder
        layoutData.put(IALFolderDescription.LOST_FOLDER_ID,ALFolder.createLostFolder());
        
	    // Setting the first layout node ID to the root folder
	    if ( firstStructId > 0 )
		 rootNode.setFirstChildNodeId(firstStructId+"");
		else 
	     rootNode.setFirstChildNodeId(null);

        // The query for getting information of the fragments
        String sqlFragment = "SELECT DISTINCT UF.NODE_ID,UF.NEXT_NODE_ID,UF.CHLD_NODE_ID,UF.PREV_NODE_ID,UF.PRNT_NODE_ID,UF.CHAN_ID,UF.NAME,UF.TYPE,UF.HIDDEN,"+
          "UF.UNREMOVABLE,UF.IMMUTABLE,UF.PRIORITY,UF.FRAGMENT_ID";
        if (RDBMServices.supportsOuterJoins) {
          sqlFragment += ",UFP.PARAM_NAME,UFP.PARAM_VALUE FROM UP_OWNER_FRAGMENT UOF, " + fragmentJoinQuery;
        } else {
          sqlFragment += " FROM UP_FRAGMENTS UF, UP_OWNER_FRAGMENT UOF WHERE ";
        }
        sqlFragment += " UF.FRAGMENT_ID=UOF.FRAGMENT_ID AND UOF.OWNER_ID=? AND UOF.FRAGMENT_ID=?";
        LogService.log(LogService.DEBUG, sqlFragment);
        PreparedStatement psFragment = con.prepareStatement(sqlFragment);
        psFragment.setInt(1,userId);
        psFragment.setInt(2,fragmentId);


        List chanIds = Collections.synchronizedList(new ArrayList());
        StringBuffer structParms = new StringBuffer();

         rs = psFragment.executeQuery();

         try {

          int lastStructId = 0;
          String sepChar = "";
          if (rs.next()) {
            int structId = rs.getInt(1);
            /*if (rs.wasNull()) {
              structId = 0;
            }*/

            readLayout: while (true) {


              int nextId = rs.getInt(2);
              /*if (rs.wasNull()) {
                nextId = 0;
              }*/


              int childId = rs.getInt(3);
              /*if (rs.wasNull()) {
                childId = 0;
              }*/

              int prevId = rs.getInt(4);
              /*if (rs.wasNull()) {
                prevId = 0;
              }*/
              int prntId = rs.getInt(5);
              /*if (rs.wasNull()) {
                prntId = 0;
              }*/
              int chanId = rs.getInt(6);
              /*if (rs.wasNull()) {
                chanId = 0;
              }*/


              int fragmentNodeId = 0;

              IALNodeDescription nodeDesc= null;
              // Trying to get the node if it already exists
              //ALNode node = (ALNode) layout.get(structId+"");
              ALNode node;
              String childIdStr = null;
              if ( childId > 0 ) {
                node = new ALFolder();
                IALFolderDescription folderDesc = new ALFolderDescription();
                // If children exist in the folder
                ((ALFolder)node).setFirstChildNodeId(childId+"");
                String type = rs.getString(8);
                int intType;
                if ( "header".equalsIgnoreCase(type))
                 intType = UserLayoutFolderDescription.HEADER_TYPE;
                else if ( "footer".equalsIgnoreCase(type))
                 intType = UserLayoutFolderDescription.FOOTER_TYPE;
                else
                 intType = UserLayoutFolderDescription.REGULAR_TYPE;

                folderDesc.setFolderType(intType);
                nodeDesc = folderDesc;
              } else {
                 node = new ALChannel();
                 ALChannelDescription channelDesc = new ALChannelDescription();
                 channelDesc.setChannelPublishId(rs.getString(6));
                 nodeDesc = channelDesc;
                }

              // Setting node description attributes
              if ( node.getNodeType() == IUserLayoutNodeDescription.FOLDER )
                 nodeDesc.setName(rs.getString(7));
              nodeDesc.setHidden(("Y".equalsIgnoreCase(rs.getString(9))?true:false));
              nodeDesc.setImmutable(("Y".equalsIgnoreCase(rs.getString(11))?true:false));
              nodeDesc.setUnremovable(("Y".equalsIgnoreCase(rs.getString(10))?true:false));
              node.setPriority(rs.getInt(12));


              nodeDesc.setFragmentId(fragmentIdStr);

              // Setting the node id
              nodeDesc.setId(structId+"");


              // Setting the next node id
              if ( nextId != 0 ) {
               node.setNextNodeId(nextId+"");
              }

              String parentId;
              switch ( prntId ) {

               case 0:
                               parentId = IALFolderDescription.ROOT_FOLDER_ID;
                               break;
               case LOST_FOLDER_ID:
                               parentId = IALFolderDescription.LOST_FOLDER_ID;
                               break;
               default:
                               parentId = prntId+"";

              }

              // Setting up the parent id
              node.setParentNodeId(parentId);

              // Setting the previous node id
              if ( prevId != 0 ) {
               node.setPreviousNodeId(prevId+"");
              }

              lastStructId = structId;


              // Putting the node into the layout hashtable with an appropriate key
              node.setNodeDescription(nodeDesc);
              layoutData.put(nodeDesc.getId(),node);

              // If there is a channel we need to get its parameters
              IALChannelDescription channelDesc = null;
              if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                channelDesc = (IALChannelDescription) nodeDesc;
                chanIds.add(nodeDesc.getId());
              }

              // getting restrictions for the nodes
              PreparedStatement psRestr = null;
              psRestr = con.prepareStatement(restrFragmentSQL);
              psRestr.setInt(1,fragmentId);
              psRestr.setInt(2,structId);

              ResultSet rsRestr = psRestr.executeQuery();
              while (rsRestr.next()) {
                  int restrType = rsRestr.getInt(1);
                  String restrExp = rsRestr.getString(2);
                  String restrPath = rsRestr.getString(3);
                  if ( restrPath == null || restrPath.trim().length() == 0 )
                    restrPath = UserLayoutRestriction.LOCAL_RESTRICTION;
                  IUserLayoutRestriction restriction = UserLayoutRestrictionFactory.createRestriction(restrType,restrExp,restrPath);
                  nodeDesc.addRestriction(restriction);
              }
               rsRestr.close();
               if ( psRestr != null ) psRestr.close();

              if (RDBMServices.supportsOuterJoins) {
                do {
                  String name = rs.getString(14);
                  String value = rs.getString(15); // Oracle JDBC requires us to do this for longs
                  if (name != null) { // may not be there because of the join
                      if ( channelDesc != null )
                          channelDesc.setParameterValue(name,value);
                  }


                  if (!rs.next()) {
                    break readLayout;
                  }
                  structId = rs.getInt(1);
                  if (rs.wasNull()) {
                    structId = 0;
                  }
                } while (structId == lastStructId);
              } else { // Do second SELECT later on for structure parameters

                  // Adding the channel ID to the String buffer
                  if ( node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                   structParms.append(sepChar + chanId);
                   sepChar = ",";
                  }

                 if (rs.next()) {
                  structId = rs.getInt(1);
                  if (rs.wasNull()) {
                    structId = 0;
                  }
                 } else {
                    break readLayout;
                   }
                } //end else

                // Setting up the priority values based on the appropriate priority restrictions
                PriorityRestriction priorityRestriction = AggregatedLayoutManager.getPriorityRestriction(node);
                if ( priorityRestriction != null ) {
                 int priority = node.getPriority();
                 int[] range = priorityRestriction.getRange();

                 int newPriority = priority;
                 if ( range[0] > priority )
                     newPriority = range[0];
                 else if ( range[1] < priority )
                     newPriority = range[1];

                 // Changing the node priority if it's been changed
                 if ( newPriority != priority )
                     node.setPriority(newPriority);
                }


            } // while


          }
        } finally {
          rs.close();
        }


        // We have to retrieve the channel defition after the layout structure
        // since retrieving the channel data from the DB may interfere with the
        // layout structure ResultSet (in other words, Oracle is a pain to program for)
        if (chanIds.size() > 0) {

              for (int i = 0; i < chanIds.size(); i++) {

                String key = (String) chanIds.get(i);

                ALNode node = (ALNode) layoutData.get(key);

                fillChannelDescription( (IALChannelDescription) node.getNodeDescription() );

              } // end for

          chanIds.clear();
        }

        if ( !RDBMServices.supportsOuterJoins && structParms.length() > 0 ) { // Pick up structure parameters
          String sql = "SELECT NODE_ID, PARAM_NAME, PARAM_VALUE FROM UP_FRAGMENT_PARAM WHERE FRAGMENT_ID=" + fragmentId +
            " AND NODE_ID IN (" + structParms.toString() + ") ORDER BY NODE_ID";
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getFragment(): " + sql);

          // Adding this to prevent the error "closed statement" in Oracle
          Statement st = con.createStatement();

          rs = st.executeQuery(sql);
          try {
            if (rs.next()) {
              int structId = rs.getInt(1);
              readParm: while(true) {
                //LayoutStructure ls = (LayoutStructure)layoutStructure.get(new Integer(structId));
                ALNode node = (ALNode) layoutData.get(structId+"");
                if ( node != null && node.getNodeType() == IUserLayoutNodeDescription.CHANNEL ) {
                 IALChannelDescription channelDesc = (IALChannelDescription) node.getNodeDescription();
                 int lastStructId = structId;
                 do {
                   String name = rs.getString(2);
                   String value = rs.getString(3);
                   channelDesc.setParameterValue(name,value);
                   if (!rs.next()) {
                     break readParm;
                   }
                 } while ((structId = rs.getInt(1)) == lastStructId);
                } else break readParm; // if else
              }
            }
          } finally {
            rs.close();
            st.close();
          }
        }

          if ( psFragment != null ) psFragment.close();
          if ( stmt != null ) stmt.close();

          long stopTime = System.currentTimeMillis();
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getFragment(): Layout document for user " + userId + " took " +
            (stopTime - startTime) + " milliseconds to create");


    } catch ( Exception e ) {
         e.printStackTrace();
         LogService.log(LogService.ERROR,e);
         throw new PortalException(e);
      } finally {
          RDBMServices.releaseConnection(con);
    }

           layout.setLayoutData ( layoutData );
           return layout;
  }


  private void fillChannelDescription( IALChannelDescription channelDesc ) throws Exception {

               String publishId =  channelDesc.getChannelPublishId();

              if ( publishId != null ) {

               ChannelDefinition channelDef = crs.getChannelDefinition(CommonUtils.parseInt(publishId));

               if ( channelDef != null ) {

                 channelDesc.setChannelTypeId(channelDef.getTypeId()+"");
                 channelDesc.setClassName(channelDef.getJavaClass());
                 channelDesc.setDescription(channelDef.getDescription());
                 channelDesc.setEditable(channelDef.isEditable());
                 channelDesc.setFunctionalName(CommonUtils.nvl(channelDef.getFName()));
                 channelDesc.setHasAbout(channelDef.hasAbout());
                 channelDesc.setHasHelp(channelDef.hasHelp());
                 channelDesc.setIsSecure(channelDef.isSecure());
                 channelDesc.setName(channelDef.getName());
                 channelDesc.setTitle(channelDef.getTitle());
                 channelDesc.setChannelPublishId(channelDef.getId()+"");
                 ChannelParameter[] channelParams = channelDef.getParameters();

                 for ( int j = 0; j < channelParams.length; j++ ) {
                  String paramName = channelParams[j].getName();
                  if ( channelDesc.getParameterValue(paramName) == null ) {
                   channelDesc.setParameterOverride(paramName,channelParams[j].getOverride());
                   channelDesc.setParameterValue(paramName,channelParams[j].getValue());
                  }
                 }
                 channelDesc.setTimeout(channelDef.getTimeout());
                 channelDesc.setTitle(channelDef.getTitle());

               }
              }

  }

    /**
     * Returns the next fragment ID.
     *
     * @return a <code>String</code> next fragment ID
     * @exception PortalException if an error occurs
     */
    public synchronized String getNextFragmentId() throws PortalException {
     int attemptsNumber = 20;
     Statement stmt = null;
     try {
      Connection con = RDBMServices.getConnection();
      try {
        RDBMServices.setAutoCommit(con, false);
        stmt = con.createStatement();
        String sQuery = "SELECT SEQUENCE_VALUE FROM UP_SEQUENCE WHERE SEQUENCE_NAME='UP_FRAGMENT'";
        for (int i = 0; i < attemptsNumber; i++) {
         try {
          LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getNextFragmentId(): " + sQuery);
          ResultSet rs = stmt.executeQuery(sQuery);
          int currentId = 0;
          rs.next();
          currentId = rs.getInt(1);
          if ( rs != null ) rs.close();
            String sUpdate = "UPDATE UP_SEQUENCE SET SEQUENCE_VALUE="+(currentId + 1)+" WHERE SEQUENCE_NAME='UP_FRAGMENT'";
            LogService.log(LogService.DEBUG, "AggregatedUserLayoutStore::getNextFragmentId(): " + sUpdate);
            stmt.executeUpdate(sUpdate);
            RDBMServices.commit(con);
            return new String (  (currentId + 1) + "" );
          } catch (Exception sqle) {
            RDBMServices.rollback(con);
            // Assume a concurrent update. Try again after some random amount of milliseconds.
            Thread.sleep(500); // Retry in up to 1/2 seconds
          }
        }
      } finally {
            if ( stmt != null ) stmt.close();
            RDBMServices.releaseConnection(con);
         }
     } catch ( Exception e ) {
        throw new PortalException(e);
       }
        throw new PortalException("Unable to generate a new next fragment node id!");
    }

    public ThemeStylesheetUserPreferences getThemeStylesheetUserPreferences (IPerson person, int profileId, int stylesheetId) throws Exception {
    int userId = person.getID();
    ThemeStylesheetUserPreferences tsup;
    Connection con = RDBMServices.getConnection();
    try {
      Statement stmt = con.createStatement();
      try {
        // get stylesheet description
        ThemeStylesheetDescription tsd = getThemeStylesheetDescription(stylesheetId);
        // get user defined defaults
        String sQuery = "SELECT PARAM_NAME, PARAM_VAL FROM UP_SS_USER_PARM WHERE USER_ID=" + userId + " AND PROFILE_ID="
            + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2";
        LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getThemeStylesheetUserPreferences(): " + sQuery);
        ResultSet rs = stmt.executeQuery(sQuery);
        try {
          while (rs.next()) {
            // stylesheet param
            tsd.setStylesheetParameterDefaultValue(rs.getString(1), rs.getString(2));
          }
        } finally {
          rs.close();
        }
        tsup = new ThemeStylesheetUserPreferences();
        tsup.setStylesheetId(stylesheetId);
        // fill stylesheet description with defaults
        for (Enumeration e = tsd.getStylesheetParameterNames(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          tsup.putParameterValue(pName, tsd.getStylesheetParameterDefaultValue(pName));
        }
        for (Enumeration e = tsd.getChannelAttributeNames(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          tsup.addChannelAttribute(pName, tsd.getChannelAttributeDefaultValue(pName));
        }
        // get user preferences
        sQuery = "SELECT PARAM_TYPE, PARAM_NAME, PARAM_VAL, ULS.NODE_ID, CHAN_ID FROM UP_SS_USER_ATTS UUSA, UP_LAYOUT_STRUCT_AGGR ULS WHERE UUSA.USER_ID=" + userId + " AND PROFILE_ID="
            + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=2 AND UUSA.STRUCT_ID = ULS.NODE_ID AND UUSA.USER_ID = ULS.USER_ID";
        LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getThemeStylesheetUserPreferences(): " + sQuery);
        rs = stmt.executeQuery(sQuery);
        try {
          while (rs.next()) {
            int param_type = rs.getInt(1);
            if (rs.wasNull()) {
              param_type = 0;
            }
            int structId = rs.getInt(4);
            if (rs.wasNull()) {
              structId = 0;
            }
            int chanId = rs.getInt(5);
            if (rs.wasNull()) {
              chanId = 0;
            }
            if (param_type == 1) {
              // stylesheet param
              LogService.log(LogService.ERROR, "AggregatedUserLayoutStore::getThemeStylesheetUserPreferences() :  stylesheet global params should be specified in the user defaults table ! UP_SS_USER_ATTS is corrupt. (userId="
                  + Integer.toString(userId) + ", profileId=" + Integer.toString(profileId) + ", stylesheetId=" + Integer.toString(stylesheetId)
                  + ", param_name=\"" + rs.getString(2) + "\", param_type=" + Integer.toString(param_type));
            }
            else if (param_type == 2) {
              // folder attribute
              LogService.log(LogService.ERROR, "AggregatedUserLayoutStore::getThemeStylesheetUserPreferences() :  folder attribute specified for the theme stylesheet! UP_SS_USER_ATTS corrupt. (userId="
                  + Integer.toString(userId) + ", profileId=" + Integer.toString(profileId) + ", stylesheetId=" + Integer.toString(stylesheetId)
                  + ", param_name=\"" + rs.getString(2) + "\", param_type=" + Integer.toString(param_type));
            }
            else if (param_type == 3) {
              // channel attribute
              tsup.setChannelAttributeValue(getStructId(structId,chanId), rs.getString(2), rs.getString(3));
            }
            else {
              // unknown param type
              LogService.log(LogService.ERROR, "AggregatedUserLayoutStore::getThemeStylesheetUserPreferences() : unknown param type encountered! DB corrupt. (userId="
                  + Integer.toString(userId) + ", profileId=" + Integer.toString(profileId) + ", stylesheetId=" + Integer.toString(stylesheetId)
                  + ", param_name=\"" + rs.getString(2) + "\", param_type=" + Integer.toString(param_type));
            }
          }
        } finally {
          rs.close();
        }
      } finally {
        stmt.close();
      }
    } finally {
      RDBMServices.releaseConnection(con);
    }
    return  tsup;
  }

  public StructureStylesheetUserPreferences getStructureStylesheetUserPreferences (IPerson person, int profileId, int stylesheetId) throws Exception {
    int userId = person.getID();
    StructureStylesheetUserPreferences ssup;
    Connection con = RDBMServices.getConnection();
    try {
      Statement stmt = con.createStatement();
      try {
        // get stylesheet description
        StructureStylesheetDescription ssd = getStructureStylesheetDescription(stylesheetId);
        // get user defined defaults
        String subSelectString = "SELECT LAYOUT_ID FROM UP_USER_PROFILE WHERE USER_ID=" + userId + " AND PROFILE_ID=" +
            profileId;
        LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getStructureStylesheetUserPreferences(): " + subSelectString);
        int layoutId;
        ResultSet rs = stmt.executeQuery(subSelectString);
        try {
          rs.next();
          layoutId = rs.getInt(1);
          if (rs.wasNull()) {
            layoutId = 0;
          }
        } finally {
          rs.close();
        }

        if (layoutId == 0) { // First time, grab the default layout for this user
          String sQuery = "SELECT USER_DFLT_USR_ID FROM UP_USER WHERE USER_ID=" + userId;
          LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getStructureStylesheetUserPreferences(): " + sQuery);
          rs = stmt.executeQuery(sQuery);
          try {
            rs.next();
            userId = rs.getInt(1);
          } finally {
            rs.close();
          }
        }

        String sQuery = "SELECT PARAM_NAME, PARAM_VAL FROM UP_SS_USER_PARM WHERE USER_ID=" + userId + " AND PROFILE_ID="
            + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1";
        LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getStructureStylesheetUserPreferences(): " + sQuery);
        rs = stmt.executeQuery(sQuery);
        try {
          while (rs.next()) {
            // stylesheet param
            ssd.setStylesheetParameterDefaultValue(rs.getString(1), rs.getString(2));
          }
        } finally {
          rs.close();
        }
        ssup = new StructureStylesheetUserPreferences();
        ssup.setStylesheetId(stylesheetId);
        // fill stylesheet description with defaults
        for (Enumeration e = ssd.getStylesheetParameterNames(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          ssup.putParameterValue(pName, ssd.getStylesheetParameterDefaultValue(pName));
        }
        for (Enumeration e = ssd.getChannelAttributeNames(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          ssup.addChannelAttribute(pName, ssd.getChannelAttributeDefaultValue(pName));
        }
        for (Enumeration e = ssd.getFolderAttributeNames(); e.hasMoreElements();) {
          String pName = (String)e.nextElement();
          ssup.addFolderAttribute(pName, ssd.getFolderAttributeDefaultValue(pName));
        }
        // get user preferences
        sQuery = "SELECT PARAM_NAME, PARAM_VAL, PARAM_TYPE, ULS.NODE_ID, CHAN_ID FROM UP_SS_USER_ATTS UUSA, UP_LAYOUT_STRUCT_AGGR ULS WHERE UUSA.USER_ID=" + userId + " AND PROFILE_ID="
            + profileId + " AND SS_ID=" + stylesheetId + " AND SS_TYPE=1 AND UUSA.STRUCT_ID = ULS.NODE_ID AND UUSA.USER_ID = ULS.USER_ID";
        LogService.log(LogService.DEBUG, "RDBMUserLayoutStore::getStructureStylesheetUserPreferences(): " + sQuery);
        rs = stmt.executeQuery(sQuery);
        try {
          while (rs.next()) {
            String temp1=rs.getString(1); // Access columns left to right
            String temp2=rs.getString(2);
            int param_type = rs.getInt(3);
            int structId = rs.getInt(4);
            if (rs.wasNull()) {
              structId = 0;
            }
            int chanId = rs.getInt(5);
            if (rs.wasNull()) {
              chanId = 0;
            }

            if (param_type == 1) {
              // stylesheet param
              LogService.log(LogService.ERROR, "AggregatedUserLayoutStore::getStructureStylesheetUserPreferences() :  stylesheet global params should be specified in the user defaults table ! UP_SS_USER_ATTS is corrupt. (userId="
                  + Integer.toString(userId) + ", profileId=" + Integer.toString(profileId) + ", stylesheetId=" + Integer.toString(stylesheetId)
                  + ", param_name=\"" + temp1 + "\", param_type=" + Integer.toString(param_type));
            }
            else if (param_type == 2) {
              // folder attribute
              ssup.setFolderAttributeValue(getStructId(structId,chanId), temp1, temp2);
            }
            else if (param_type == 3) {
              // channel attribute
              ssup.setChannelAttributeValue(getStructId(structId,chanId), temp1, temp2);
            }
            else {
              // unknown param type
              LogService.log(LogService.ERROR, "AggregatedUserLayoutStore::getStructureStylesheetUserPreferences() : unknown param type encountered! DB corrupt. (userId="
                  + Integer.toString(userId) + ", profileId=" + Integer.toString(profileId) + ", stylesheetId=" + Integer.toString(stylesheetId)
                  + ", param_name=\"" + temp1 + "\", param_type=" + Integer.toString(param_type));
            }
          }
        } finally {
          rs.close();
        }
      } finally {
        stmt.close();
      }
    } finally {
      RDBMServices.releaseConnection(con);
    }
    return  ssup;
  }

  /**
     * Returns the list of pushed fragment node IDs that must be removed from the user layout.
     * @param person an <code>IPerson</code> object specifying the user
     * @param profile a user profile for which the layout is being stored
     * @return a <code>Enumeration</code> list containing the fragment node IDs to be deleted from the user layout
     * @exception PortalException if an error occurs
     */
 public Enumeration getIncorrectPushedFragmentNodes (IPerson person, UserProfile profile) throws PortalException {
  int userId = person.getID();
  int layoutId = profile.getLayoutId();
  Vector incorrectIds = new Vector();
  Connection con = RDBMServices.getConnection();
  try {
    IGroupMember groupPerson = null;
    String query1 = "SELECT ULS.FRAGMENT_ID,ULS.NODE_ID,UGF.GROUP_KEY FROM UP_LAYOUT_STRUCT_AGGR ULS,UP_OWNER_FRAGMENT UOF,UP_GROUP_FRAGMENT UGF WHERE "+
    "UOF.PUSHED_FRAGMENT='Y' AND ULS.USER_ID="+userId+" AND ULS.LAYOUT_ID="+layoutId+" AND ULS.FRAGMENT_ID=UOF.FRAGMENT_ID AND ULS.FRAGMENT_ID=UGF.FRAGMENT_ID";
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery(query1);
    String fragmentIds = null;
    Set groupKeys = new HashSet();

    while ( rs.next() ) {
     if ( groupPerson == null ) {
      EntityIdentifier personIdentifier = person.getEntityIdentifier();
      groupPerson = GroupService.getGroupMember(personIdentifier);
     }
      int fragmentId = rs.getInt(1);
      int nodeId = rs.getInt(2);
      String groupKey = rs.getString(3);
      if ( groupKeys.contains(groupKey) )
         incorrectIds.add(nodeId+"");
      else {
        IEntityGroup group = GroupService.findGroup(groupKey);
        if ( group == null || !groupPerson.isDeepMemberOf(group) ) {
         incorrectIds.add(nodeId+"");
         groupKeys.add(groupKey);
        }
      }
    }
      if ( stmt != null ) stmt.close();
  } catch ( Exception e ) {
       throw new PortalException(e);
    } finally {
       RDBMServices.releaseConnection(con);
      }
    return incorrectIds.elements();
 }

}

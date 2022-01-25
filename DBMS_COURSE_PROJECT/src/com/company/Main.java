package com.company;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

public class Main {
    private static boolean satisfies_brackets(String query){
        Stack<Character> st=new Stack<>();
        for(char c:query.toCharArray()){
            if(c=='(')
                st.push(c);
            else if(c==')'){
                if(st.isEmpty())
                    return false;
                st.pop();
            }
        }

        return st.isEmpty();
    }

    private static boolean create_query_validation(String[] attributes){
        Set<String> set=new HashSet<>();
        for(String attri:attributes){
            if(attri.contains("primary key")){
                String pk=attri.split("[\\(\\)]")[1];
                if(!set.contains(pk))
                    return false;
            }
            else if(attri.contains("foreign key")){
                int refPos=attri.indexOf(" references ");
                if(refPos==-1)
                    return false;
                int brackAfterRef=attri.indexOf("(",refPos+12);
                String foreignTable=attri.substring(refPos+12,brackAfterRef);
                File table=new File("src\\tables\\"+foreignTable+".txt");
                if(!table.exists()){
                    System.out.println("Referenced table does not exist");
                    return false;
                }
            }
            else{
                String[] attriCol=attri.split(" ");
                if(!attriCol[1].equals("int") && !attriCol[1].equals("decimal") && !attriCol[1].contains("char("))
                    return false;
                set.add(attriCol[0]);
            }
        }
        return true;
    }
    private static void create_table(String[] tokens,String query){
        //create table student(roll int check(roll>0),name char(10),PRIMARY KEY(roll),FOREIGN KEY(name) REFERENCES emp(name));
        query=query.substring(0,query.length()-1);
        String[] data=query.split("\\(",2);
        String tableName=(data[0].split(" "))[2];
        String resString=tableName;
        String[] attributes=data[1].split(",");

        if(!create_query_validation(attributes)){
            System.out.println("Syntax Error");
            return;
        }
        int n=attributes.length;
        for(int i=0;i<n;i++){
            if(attributes[i].contains("foreign key")){
                resString+="$"+attributes[i].trim();
            }
            else if(attributes[i].contains("primary key")) {
                boolean flag = false;
                while (!attributes[i].contains(")")) {
                    resString += "$" + attributes[i].trim() + "#";
                    flag = true;
                    i++;
                }
                if (flag == true)
                    resString += attributes[i].trim();
                else
                    resString += "$" + attributes[i].trim();
            }
            else{
            	String[] attriToken=attributes[i].trim().split(" ",3);
            	String colInfo="";
            	for(String at:attriToken)
            		colInfo+=at+"#";
                resString+="$"+colInfo.substring(0,colInfo.length()-1);
            }
        }

        try{
            Writer output = new BufferedWriter(new FileWriter("src\\db\\schema.txt", true));
            File newTable=new File("src\\tables\\"+tableName+".txt");
            boolean status=newTable.createNewFile();
            if(status){
                output.write(resString+"\r\n");
                output.close();
                System.out.println("Table created");
            }
            else
                System.out.println("File already exists");
        }
        catch(IOException e){
            System.out.println(e);
        }
    }

    private static boolean insert_query_isValid(String[] tokens) throws IOException {
        int firstBracket=tokens[3].indexOf("(");
        int lastBracket=tokens[3].lastIndexOf(")");
        if(firstBracket==-1 || lastBracket==-1 || !tokens[3].contains("values(")){
            System.out.println("Invalid Syntax");
            return false;
        }

        String[] colValues=tokens[3].substring(firstBracket+1,lastBracket).split(",");
        String[] schemaTokens=get_schema_of_table(tokens[2]);
        ArrayList<String> colDataTypes=new ArrayList<>();

        for(int i=1;i<schemaTokens.length;i++){
            if(!schemaTokens[i].contains("primary key") && !schemaTokens[i].contains("foreign key"))
                colDataTypes.add(schemaTokens[i].split("#")[1]);
        }

        if(colValues.length!=colDataTypes.size()){
            System.out.println("Fields count does not match");
            return false;
        }

        for(int i=0;i<colValues.length;i++){
            if(colDataTypes.get(i).equals("int")){
                try{
                    Integer.parseInt(colValues[i]);
                }
                catch (NumberFormatException nfe){
                    System.out.println("Data type does not match");
                    return false;
                }
            }
            else if(colDataTypes.get(i).equals("decimal")){
                try{
                    Double.parseDouble(colValues[i]);
                }
                catch (NumberFormatException nfe){
                    System.out.println("Data type does not match");
                    return false;
                }
            }
            else{
                try{
                    int strlen=Integer.parseInt(colDataTypes.get(i).split("[\\(\\)]")[1]);
                    if(colValues[i].length()>strlen){
                        System.out.println("String length exceeds");
                        return false;
                    }

                }
                catch (NumberFormatException nfe){
                    System.out.println("Data type does not match");
                    return false;
                }
            }
        }
        return true;
    }

    private static void insert_entries(String[] tokens,String query) throws IOException {
        //insert into student values(90,100,"pratham");

        String table_name=(tokens[2].split("\\("))[0];
        File table=new File("src\\tables\\"+table_name+".txt");

        if(table.exists()){
            if(!insert_query_isValid(tokens))       //validating insert query
                return;

            FileWriter file=new FileWriter("src\\tables\\"+table_name+".txt",true);
            HashMap<String,String> map=new HashMap<>();
            ArrayList<String> sequenceList=new ArrayList<>();
            map_cols_with_values(query,tokens,map);    //map values with column names
            get_sequence_of_cols(sequenceList,table_name);  //get sequence of columns in table

            if(table.length()!=0 && !satisfies_primaryKey_constraint(query,tokens,map,sequenceList)){
            	System.out.println("Primary Key Constraint not followed");
                return;
            }

            if(!satisfies_foreignKey_constraint(tokens,table_name,map)){
                System.out.println("Foreign Key Constraint not followed");
                return;
            }

            if(!satisfies_domain_constraint(table_name,query,sequenceList)){
                System.out.println("Domain Constraint not followed");
                return;
            } 

            Writer output = new BufferedWriter(file);
            int startBrack=query.indexOf("(");
            int endBrack=query.indexOf(")");
            if(startBrack==-1 || endBrack==-1){
                System.out.println("brackets are missing");
                return;
            }
            String resString=query.substring(startBrack+1,endBrack).replace(",","#");
            output.write(resString+"\r\n");
            output.close();
            file.close();
            System.out.println("Tuple inserted successfully");
        }
        else
            System.out.println("Table does not exist");
    }

    private static boolean satisfies_domain_constraint(String table_name,String query, ArrayList<String> sequenceList) throws IOException {
		String[] schemaTokens=get_schema_of_table(table_name);
		ArrayList<String> constraintList=new ArrayList<>();
		
		for(String st:schemaTokens) {
			if(st.equals(table_name))
				continue;
			if(st.contains("primary") || st.contains("foreign"))
				break;
			String[] attributeToken=st.split("#");
			if(attributeToken.length==3)
				constraintList.add(attributeToken[2].split("[\\(\\)]")[1]);
		}
		
		String[] tableContents=(query.substring(query.indexOf("(")+1, query.length()-1)).split(",");
		
		for(String constraint:constraintList) {
			boolean flag=false;
	        if(constraint.contains("and")){     //if condition contains and
	            String[] condition=constraint.split(" and ");    //creates an array of all expressions in where condition

                for(String expression:condition){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    //System.out.println(expression);
                    String operator=find_operator(expression);  //finding operator
                    //System.out.println(operator);
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    //System.out.println(tableContents[attriIndex]);
                    if(!is_condition_valid(tableContents,attriIndex,operator,operands[1])) 
                    	return false;
                }
	        }
	        else{
	        	String[] condition=constraint.split(" or ");    //creates an array of all expressions in where condition

                for(String expression:condition){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    //System.out.println(expression);
                    String operator=find_operator(expression);  //finding operator
                    //System.out.println(operator);
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    //System.out.println(tableContents[attriIndex]);
                    if(is_condition_valid(tableContents,attriIndex,operator,operands[1])) 
                    	flag=true;
                }
                if(!flag)
                	return false;
	        }
		}
    	return true;
	}

	private static boolean satisfies_foreignKey_constraint(String[] tokens, String tableName, HashMap<String, String> map) throws IOException {
        String[] schemaTokens=get_schema_of_table(tableName);
        ArrayList<String> foreignKey=new ArrayList<>();
        for(String tk:schemaTokens){        //putting foreign key constraints in a list
            if(tk.contains("foreign key")){
                foreignKey.add(tk);
            }
        }

        for(String fk:foreignKey){      //traverse through all foreign key constraints of table
            String fkValue=map.get(fk.substring(fk.indexOf("(")+1,fk.indexOf(")")));
            int lastOpenBrack=fk.lastIndexOf("(");
            int lastCloseBrack=fk.lastIndexOf(")");
            String fkTable=fk.substring(fk.lastIndexOf(" ")+1,lastOpenBrack);
            String fkTableCol=fk.substring(lastOpenBrack+1,lastCloseBrack);
            String[] fkTableSchemaTokens=get_schema_of_table(fkTable);
            int fkColPos=0;
            for(String tk:fkTableSchemaTokens){     //finding position of foreign key column in table using schema of table
                if(tk.contains("primary key") || tk.contains("foreign key")){
                    break;
                }
                int hashPos=tk.indexOf("#");
                if(hashPos!=-1){        //this condition will check if the token is a column or table name,since table name will not contain #
                    String colName=tk.substring(0,hashPos);
                    if(colName.equals(fkTableCol))
                        break;
                    fkColPos++;
                }
            }

            File fkTableFile=new File("src\\tables\\"+fkTable+".txt");
            FileReader fr=new FileReader(fkTableFile);
            BufferedReader br=new BufferedReader(fr);
            String line;

            while((line=br.readLine())!=null){      //reading content of foreign key referenced table
                String[] entries=line.split("#");
                if(entries[fkColPos].equals(fkValue))       //check if value we want to insert is already present in referenced table
                    return true;
            }
            return false;       //if value is not present in referenced table
        }
        return true;    //if table does not contain foreign key constraint
    }

    private static boolean satisfies_primaryKey_constraint(String query,String[] tokens,HashMap<String,String> map,ArrayList<String> sequenceList) throws IOException {
        ArrayList<String> pk=new ArrayList<>();
        String tableName=tokens[2];
        //System.out.println(schema);
        String[] schemaTokens=get_schema_of_table(tableName);       //get schema of the table
        for(String st:schemaTokens){
            if(st.contains("primary key")){
                st=st.substring(0,st.length()-1);
                String[] primary=((st.split("\\("))[1]).split("#");   //Putting primary keys in array
                for(String p:primary)       //adding primary keys to a set
                    pk.add(p);
            }
        }
        String compositeKey="";
        for(String p:pk){       //creating string for primary keys
            //System.out.println(p);
        	String value=map.get(p);
        	if(value==null)
        		return true;
            compositeKey+=value+"#";
        }
        compositeKey=compositeKey.substring(0,compositeKey.length()-1); //removes last added #
        //System.out.println(compositeKey);
        HashMap<Integer,String> indexToPK=new HashMap<>();
        for(String primaryKey:pk){      //map index of column with primary key
        	indexToPK.put(sequenceList.indexOf(primaryKey),primaryKey);
        }

        File tableFile=new File("src\\tables\\"+tableName+".txt");
        FileReader fr=new FileReader(tableFile);
        BufferedReader br=new BufferedReader(fr);
        String line;

        while((line=br.readLine())!=null){      //reading lines in schema file to get schema of required table
            String compositeKey1="";
            String[] entries=line.split("#");
            for(int index:indexToPK.keySet()){
                compositeKey1+=entries[index]+"#";
            }
            //System.out.println(compositeKey1);
            compositeKey1=compositeKey1.substring(0,compositeKey1.length()-1);

            if(compositeKey.equals(compositeKey1))
                return false;
        }
        return true;
    }

    private static void get_sequence_of_cols(ArrayList<String> sequenceList, String table_name) throws IOException {
        File schemaFile=new File("src\\db\\schema.txt");
        FileReader fr=new FileReader(schemaFile);
        BufferedReader br=new BufferedReader(fr);
        String line;
        while((line=br.readLine())!=null){      //reading lines in schema file to get schema of required table
            String tableName=(line.split("\\$"))[0];
            if(tableName.equals(table_name))
                break;
        }
        br.close();
        String[] temp=line.split("\\$");
        for(String tk:temp){
            if(!tk.contains("primary key") && !tk.contains("foreign key")){
                int hashPos=tk.indexOf("#");
                if(hashPos!=-1){
                    String colName=tk.substring(0,hashPos);
                    sequenceList.add(colName);
                }
            }
        }
    }

    private static void map_cols_with_values(String query, String[] tokens, HashMap<String, String> map) throws IOException {
        String tableName=tokens[2];       //get table name from query
        String[] schemaTokens=get_schema_of_table(tableName);       //get schema of the table
        ArrayList<String> colsList=new ArrayList<>();

        for(String tk:schemaTokens){        //adding attribute names to column list
            if(!tk.contains("primary key") && !tk.contains("foreign key")){
                int hashPos=tk.indexOf("#");
                if(hashPos!=-1){
                    String colName=tk.substring(0,hashPos);
                    colsList.add(colName);
                }
            }
        }

        String cols=query.substring(query.indexOf("(")+1,query.indexOf(")"));   //creating list of values
        String[] valuesList=cols.split(",");
        for(int i=0;i<valuesList.length;i++)    //remove whitespaces
            valuesList[i]=valuesList[i].trim();

        for(int i=0;i<colsList.size();i++){     //mapping columns with its values
            map.put(colsList.get(i),valuesList[i]);
        }
    }

    private static String[] get_schema_of_table(String tableName) throws IOException {
        File schemaFile=new File("src\\db\\schema.txt");
        FileReader fr=new FileReader(schemaFile);
        BufferedReader br=new BufferedReader(fr);
        String line;
        while((line=br.readLine())!=null){      //reading lines in schema file to get schema of required table
            String tn=(line.split("\\$"))[0];
            if(tn.equals(tableName))
                break;
        }

        return line.split("\\$");
    }

    private static void describe(String[] tokens) throws IOException{
        //describe student
        File file=new File("src\\tables\\"+tokens[1]+".txt");
        if(file.exists()){
            String[] schemaTokens=get_schema_of_table(tokens[1]);
            HashSet<String> pkSet=new HashSet<>();
            HashMap<String,String> foreign=new HashMap<>();

            for(String st:schemaTokens){
                if(st.contains("primary key")){
                    st=st.substring(0,st.length()-1);
                    String[] primary=((st.split("\\("))[1]).split("#");   //Putting primary keys in a array
                    for(String p:primary)       //adding primary keys to a set
                        pkSet.add(p);
                }
                else if(st.contains("foreign key")){
                    String fk=(st.split("[\\(\\)]"))[1];        //return foreign key
                    foreign.put(fk,st);
                }
            }

            for(int i=1;i<schemaTokens.length;i++){     //creating result string
                if(schemaTokens[i].contains("primary key") || schemaTokens[i].contains("foreign key"))
                    break;
                String[] attriTokens=schemaTokens[i].split("#");
                String colName=attriTokens[0];
                String colType=attriTokens[1];
                String constraint="";
                String pk="";
                String fk=foreign.getOrDefault(colName,"");
                if(attriTokens.length==3){
                    constraint=(attriTokens[2].split("[\\(\\)]"))[1];
                }
                if(pkSet.contains(colName))
                    pk="primary key";
                String resString=colName+"--"+colType;
                if(pk!="")
                    resString+="--"+pk;
                if(fk!="")
                    resString+="--"+fk;
                if(constraint!="")
                    resString+="--"+constraint;
                System.out.println(resString);
            }
        }
        else{
            System.out.println("Table does not exist");
        }
    }

    private static void help_tables() throws IOException {
        //help tables

        File file=new File("src\\db\\schema.txt");
        if(file.length()==0)
            System.out.println("No tables found");
        else{
            FileReader fr=new FileReader(file);
            BufferedReader br=new BufferedReader(fr);
            System.out.println("Table Names:");
            String line;
            while((line=br.readLine())!=null){
                System.out.println((line.split("\\$"))[0]);
            }
        }
    }

    private static void help_command(String[] tokens) {
        if(tokens.length==3 && tokens[1].equals("create") && tokens[2].equals("table")){
            System.out.println("This query is used to create a new table by specifying attribute name,attribute data type and optional constraints like primary key and foreign key.");
            System.out.println("Syntax of CREATE TABLE :");
            System.out.println("CREATE TABLE table_name ( attribute_1 attribute1_type CHECK (constraint1),\n" +
                    "attribute_2 attribute2_type, …, PRIMARY KEY ( attribute_1, attribute_2 ),\n" +
                    "FOREIGN KEY ( attribute_y ) REFERENCES table_x ( attribute_t ), FOREIGN\n" +
                    "KEY ( attribute_w ) REFERENCES table_y ( attribute_z )… );");
        }
        else if(tokens.length==3 && tokens[1].equals("drop") && tokens[2].equals("table")){
            System.out.println("DROP is used to delete a whole database or just a table");
            System.out.println("Syntax of DROP TABLE :");
            System.out.println("DROP TABLE table_name;");
        }
        else if(tokens.length==2 && tokens[1].equals("select")){
            System.out.println("SELECT is used to select certain columns of table using different conditions specified using WHERE clause.");
            System.out.println("Syntax of SELECT :");
            System.out.println("SELECT attribute_list FROM table_list WHERE condition_list;");
        }
        else if(tokens.length==2 && tokens[1].equals("insert")){
            System.out.println("INSERT INTO clause is used to insert tuples in specified table.");
            System.out.println("Syntax of INSERT :");
            System.out.println("INSERT INTO table_name VALUES ( val1, val2, … );");
        }
        else if(tokens.length==2 && tokens[1].equals("delete")){
            System.out.println("DELETE clause is used to delete tuples from table based on some condition");
            System.out.println("Syntax of DELETE :");
            System.out.println("DELETE FROM table_name WHERE condition_list;");
        }
        else if(tokens.length==2 && tokens[1].equals("update")){
            System.out.println("UPDATE clause is used to update or modify particular attribute value based on some condition.");
            System.out.println("Syntax of UPDATE :");
            System.out.println("UPDATE table_name SET attr1 = val1, attr2 = val2… WHERE condition_list;");
        }
        else{
            System.out.println("Query is invalid!!");
        }
    }

    private static void drop_table(String[] tokens) throws IOException {
        File table=new File("src\\tables\\"+tokens[2]+".txt");
        if(!table.exists()){
            System.out.println("Table not found!");
        }
        else{
            if(!satisfies_drop_table_foreign_constraint(tokens[2])){
                System.out.println("Other table exists which references "+tokens[2]+" table");
                return;
            }

            File tempFile=new File("src\\db\\"+"temp.txt");
            File schemaFile=new File("src\\db\\"+"schema.txt");
            tempFile.createNewFile();       //creating temporary file
            BufferedReader br = new BufferedReader(new FileReader(schemaFile));
            PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
            String line=null;

            while ((line = br.readLine()) != null) {        //read from schema file and print in temp file
                String tn=(line.split("\\$"))[0];
                if (!tn.equals(tokens[2])) {    //if schema of required table is obtained don't write it in new file
                    pw.println(line);
                    pw.flush();
                }
            }
            pw.close();
            br.close();

            table.delete();     //delete table file
            schemaFile.delete();    //delete original file
            tempFile.renameTo(schemaFile);      //rename temp file to schema.txt
            System.out.println("Table dropped successfully");
        }
    }

    private static boolean satisfies_drop_table_foreign_constraint(String tableName) throws IOException {
        File schemaFile=new File("src\\db\\"+"schema.txt");
        BufferedReader br = new BufferedReader(new FileReader(schemaFile));
        String line=null;

        while ((line = br.readLine()) != null) {        //read from schema file
            String[] schemaTokens=line.split("\\$");
            if(line.contains("foreign key") && !schemaTokens[0].equals(tableName)){
                for(int i=schemaTokens.length-1;i>=0;i--){
                    if(!schemaTokens[i].contains("foreign key"))
                        break;
                    String schemaTable=schemaTokens[i].substring(schemaTokens[i].indexOf(" references ")+12,schemaTokens[i].lastIndexOf("("));
                    if(schemaTable.equals(tableName))
                        return false;
                }
            }
        }
        br.close();
        return true;
    }

    private static String find_operator(String expression){
        if(expression.contains("<=")){
            return "<=";
        }
        else if(expression.contains(">=")){
            return ">=";
        }
        else if(expression.contains("!=")){
            return "!=";
        }
        else if(expression.contains("=")){
            return "=";
        }
        else if(expression.contains("<")){
            return "<";
        }
        else{
            return ">";
        }
    }

    private static boolean is_condition_valid(String[] tableContent, int attriIndex, String operator, String operandValue){
        if(operator.equals("<=") && Integer.parseInt(tableContent[attriIndex])<=Integer.parseInt(operandValue))
            return true;
        else if(operator.equals(">=") && Integer.parseInt(tableContent[attriIndex])>=Integer.parseInt(operandValue))
            return true;
        else if(operator.equals("!=") && !(tableContent[attriIndex].equals(operandValue)))
            return true;
        else if(operator.equals("=") && tableContent[attriIndex].equals(operandValue))
            return true;
        else if(operator.equals("<") && Integer.parseInt(tableContent[attriIndex])<Integer.parseInt(operandValue))
            return true;
        else if(operator.equals(">") && Integer.parseInt(tableContent[attriIndex])>Integer.parseInt(operandValue))
            return true;
        else
            return false;
    }

    private static void truncate(String query,String[] tokens) throws IOException {
        //truncate table student;
        File table=new File("src\\tables\\"+tokens[2]+".txt");
        if(!table.exists()){
            System.out.println("Table not found!");
            return;
        }

        table.delete();     //delete table file
        table.createNewFile();  //created new file
        System.out.println("Table truncated");
    }

    private static void delete_rows(String query,String[] tokens) throws IOException {
        //delete from student where roll=1 and name="ram";
        File tableFile=new File("src\\tables\\"+tokens[2]+".txt");
        if(!tableFile.exists()){
            System.out.println("Table not found!");
            return;
        }
        if(query.indexOf(" where ")==-1){
            BufferedReader br = new BufferedReader(new FileReader(tableFile));
            int lineCount=0;
            String line=null;
            while ((line = br.readLine()) != null) {        //read from schema file and print in temp file
                lineCount++;
            }
            br.close();
            tableFile.delete();     //delete table file
            tableFile.createNewFile();  //created new file
            System.out.println(lineCount+" rows deleted");
            return;
        }

        ArrayList<String> sequenceList =new ArrayList<>();      //contains sequence of attributes in schema
        get_sequence_of_cols(sequenceList,tokens[2]);
        String conditions=query.substring((query.indexOf("where")+6));  //gets the string after where clause
        File tempFile=new File("src\\tables\\"+"temp.txt");     //creates temp file

        tempFile.createNewFile();       //creating temporary file
        BufferedReader br = new BufferedReader(new FileReader(tableFile));      //reader for old original file
        PrintWriter pw = new PrintWriter(new FileWriter(tempFile));     //writer for new temp file
        String line=null;

        int affectedRowsCount=0;
        if(conditions.contains("and")){     //if condition contains and
            String[] constraint=conditions.split(" and ");    //creates an array of all expressions in where condition

            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                for(String expression:constraint){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    //System.out.println(expression);
                    String operator=find_operator(expression);  //finding operator
                    //System.out.println(operator);
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column does not exist");
                        return;
                    }
                    //System.out.println(attriIndex);
                    if(!is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        //System.out.println("Inside set flag");
                        break;
                    }
                }

                if (flag) {    //if expression is false then print that line in new file
                    pw.println(line);
                    pw.flush();
                }
                else
                    affectedRowsCount++;
            }
            System.out.println(affectedRowsCount+" rows affected");
        }
        else{
            String[] constraint=conditions.split(" or ");

            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                for(String expression:constraint){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    String operator=find_operator(expression);  //finding operator
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column does not exist");
                        return;
                    }
                    if(is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        break;
                    }
                }

                if (!flag) {    //if expression is false then print that line in new file
                    pw.println(line);
                    pw.flush();
                }
                else
                    affectedRowsCount++;
            }
            System.out.println(affectedRowsCount+" rows affected");
        }

        pw.close();     //close all files
        br.close();

        tableFile.delete();     //delete original table file
        tempFile.renameTo(tableFile);      //rename temp file to table file

    }

    private static boolean schema_contains_attributes(String tableName,String[] attri) throws IOException {
        String[] schemaTokens=get_schema_of_table(tableName);

        ArrayList<String> schemaCols=new ArrayList<>();
        for(int i=1;i<schemaTokens.length;i++){
            String st=schemaTokens[i];
            if(st.contains("foreign key") || st.contains("primary key"))
                break;

            String[] attriArray=st.split("#");
            schemaCols.add(attriArray[0]);
        }

        for(String col:attri){
            if(!schemaCols.contains(col)){
                return false;
            }
        }
        return true;
    }

    private static boolean select_query_validation(String query,String tableName) throws IOException {
        String fields=query.substring(7,query.indexOf(" from ")).trim();
        if(fields.equals("*"))
            return true;
        String[] fieldsArray=fields.split(",");
        if(!schema_contains_attributes(tableName,fieldsArray))
            return false;
        return true;
    }

    private static void select_query(String query) throws IOException {
        //select * from student where name="Kapil" and roll=3;

        int fromPos=query.indexOf(" from ");
        int wherePos=query.indexOf(" where ");
        if(wherePos==-1)
            wherePos=query.length();
        String tableName=query.substring(fromPos+6,wherePos);
        File tableFile=new File("src\\tables\\"+tableName+".txt");
        if(!tableFile.exists()){
            System.out.println("Table does not exists");
            return;
        }

        if(!select_query_validation(query,tableName)){  //validating query
            System.out.println("In validation Column does not exist");
            return;
        }

        String tableColumns=query.substring(7,fromPos);
        ArrayList<String> sequenceList =new ArrayList<>();      //contains sequence of attributes in schema
        get_sequence_of_cols(sequenceList,tableName);
        String conditions="";
        if(wherePos!=query.length())
            conditions=query.substring(wherePos+7);  //gets the string after where clause

        BufferedReader br = new BufferedReader(new FileReader(tableFile));      //reader for old original file
        String line=null;

        ArrayList<Integer> selectSequence=new ArrayList<>();
        sequenceOfSelectColumns(selectSequence,tableColumns,tableName);

        int affectedRowsCount=0;
        if(conditions.contains(" and ")){     //if condition contains and

            String[] constraint=conditions.split(" and ");    //creates an array of all expressions in where condition

            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                for(String expression:constraint){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    //System.out.println(expression);
                    String operator=find_operator(expression);  //finding operator
                    //System.out.println(operator);
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column does not exist");
                        return;
                    }
                    //System.out.println(attriIndex);
                    if(!is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        //System.out.println("Inside set flag");
                        break;
                    }
                }

                if (!flag) {    //if expression is false then print that line in new file
                    printSelectedColumns(selectSequence,line);
                    affectedRowsCount++;
                }

            }
            System.out.println(affectedRowsCount+" rows returned");
        }
        else{
            //System.out.println("Inside or");
            String[] constraint;
            if(conditions.contains(" or "))
                constraint=conditions.split(" or ");
            else{
                if(wherePos==query.length())
                    constraint=new String[0];
                else{
                    constraint=new String[1];
                    constraint[0]=conditions;
                }
            }

            //System.out.println(constraint.length);
            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                boolean notContainsWhereClause=true;
                for(String expression:constraint){  //take expressions one by one
                    ///System.out.println(expression);
                    notContainsWhereClause=false;
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    String operator=find_operator(expression);  //finding operator
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column does not exist");
                        return;
                    }

                    //System.out.println(tableContent[attriIndex]+" "+operator + " " + operands[1]);
                    if(is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        break;
                    }
                }

                if (flag || notContainsWhereClause) {    //if expression is false then print that line in new file
                    printSelectedColumns(selectSequence,line);
                    affectedRowsCount++;
                }
            }
            System.out.println(affectedRowsCount+" rows returned");
        }

        br.close();

    }

    private static void printSelectedColumns(ArrayList<Integer> selectSequence, String line) {
        String[] tableContent=line.split("#");
        for(int pos:selectSequence){
            System.out.print(tableContent[pos]+"   \t");
        }
        System.out.println();
    }

    private static void sequenceOfSelectColumns(ArrayList<Integer> selectSequence, String tableColumns, String tableName) throws IOException {
        String[] schemaTokens=get_schema_of_table(tableName);
        int pos=0;
        if(tableColumns.equals("*")){
            for(String st:schemaTokens){
                if(st.equals(tableName)){
                    continue;
                }

                if(st.contains("foreign key") || st.contains("primary key"))
                    break;
                System.out.print(st.split("#")[0]+"\t");
                selectSequence.add(pos);
                pos++;
            }
        }
        else{
            String[] columnTokens=tableColumns.split(",");
            for(String ct:columnTokens){
                ct=ct.trim();
                pos=0;
                for(String st:schemaTokens){
                    if(st.equals(tableName))
                        continue;
                    if(st.contains("foreign key") || st.contains("primary key"))
                        break;
                    if(st.split("#")[0].equals(ct))
                        selectSequence.add(pos);
                    pos++;
                }
                System.out.print(ct+"\t");
            }
        }
        System.out.println();

    }

    private static void update_rows(String query, String[] tokens) throws IOException {
		//update student set name="Kapil" where roll=3;
        File tableFile=new File("src\\tables\\"+tokens[1]+".txt");
        if(!tableFile.exists()){
            System.out.println(tokens[1]+"table does not exists");
            return;
        }

        int setPos=query.indexOf(" set ");
        int wherePos=query.indexOf(" where ");
        if(wherePos==-1)
            wherePos=query.length();
        String setFieldsString=query.substring(setPos+5,wherePos).trim();
        String[] setFieldsTokens=setFieldsString.split(",");
        ArrayList<String> setFieldsAttri=new ArrayList<>();
        ArrayList<String> setFieldsValue=new ArrayList<>();
        ArrayList<String> sequenceList =new ArrayList<>();      //contains sequence of attributes in schema
        get_sequence_of_cols(sequenceList,tokens[1]);   //get sequence of columns of table
//        for(String s:sequenceList){
//            System.out.println(s);
//        }

        for(String sf:setFieldsTokens){     //separate fields and values
            String[] sfTokens=sf.split("=");
            if(!sequenceList.contains(sfTokens[0].trim())){
                System.out.println("Column in set field does not exist");
                return;
            }
            setFieldsAttri.add(sfTokens[0]);
            setFieldsValue.add(sfTokens[1]);
        }

        ArrayList<Integer> setFieldsAttriIndex=new ArrayList<>();
        String[] schemaTokens=get_schema_of_table(tokens[1]);
        int pos=0;
        for(String ct:setFieldsAttri){      //get index of attributes in query
            ct=ct.trim();
            pos=0;
            for(String st:schemaTokens){
                if(st.equals(tokens[1]))
                    continue;
                if(st.contains("foreign key") || st.contains("primary key"))
                    break;
                if(st.split("#")[0].equals(ct))
                    setFieldsAttriIndex.add(pos);
                pos++;
            }
        }

        Map<Integer,String> indexValueMap=new HashMap<>();
        for(int i=0;i<setFieldsAttriIndex.size();i++)   //map column index to value
            indexValueMap.put(setFieldsAttriIndex.get(i),setFieldsValue.get(i));

        String conditions="";
        if(wherePos!=query.length())
            conditions=query.substring(wherePos+7);  //gets the string after where clause

        File tempFile=new File("src\\tables\\"+"temp.txt");     //creates temp file
        tempFile.createNewFile();       //creating temporary file
        BufferedReader br = new BufferedReader(new FileReader(tableFile));      //reader for old original file
        PrintWriter pw = new PrintWriter(new FileWriter(tempFile));     //writer for new temp file
        String line=null;

        int affectedRowsCount=0;
        if(conditions.contains(" and ")){     //if condition contains and
            String[] constraint=conditions.split(" and ");    //creates an array of all expressions in where condition

            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                for(String expression:constraint){  //take expressions one by one
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    //System.out.println(expression);
                    String operator=find_operator(expression);  //finding operator
                    //System.out.println(operator);
                    String[] operands=expression.split(operator);   //get operands

                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column in where condition does not exist");
                        return;
                    }
                    //System.out.println(attriIndex);
                    if(!is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        //System.out.println("Inside set flag");
                        break;
                    }
                }

                if (flag) {    //if expression is false then print that line in new file
                    pw.println(line);
                    pw.flush();
                }
                else{
                    String[] contentArray=line.split("#");
                    String updatedString="";
                    for(int i=0;i<contentArray.length;i++){
                        if(indexValueMap.containsKey(i))
                            updatedString+=indexValueMap.get(i)+"#";
                        else
                            updatedString+=contentArray[i]+"#";
                    }
                    pw.println(updatedString);
                    pw.flush();
                    affectedRowsCount++;
                }

            }
            System.out.println(affectedRowsCount+" rows affected");
        }
        else{
            String[] constraint;
            if(conditions.contains(" or "))
                constraint=conditions.split(" or ");
            else{
                if(wherePos==query.length())
                    constraint=new String[0];
                else{
                    constraint=new String[1];
                    constraint[0]=conditions;
                }
            }

            while ((line = br.readLine()) != null) {        //read from table file
                String[] tableContent=(line.split("#"));    //create array of table fields

                boolean flag=false;
                boolean notContainsWhereClause=true;
                for(String expression:constraint){  //take expressions one by one
                    ///System.out.println(expression);
                    notContainsWhereClause=false;
                    expression=expression.replaceAll("\\s","");   //remove all spaces from expression
                    String operator=find_operator(expression);  //finding operator
                    String[] operands=expression.split(operator);   //get operands

                    //System.out.println(operands[0]);
                    int attriIndex=sequenceList.indexOf(operands[0]);    //get index of attribute in schema
                    if(attriIndex==-1){
                        System.out.println("Column in where condition does not exist");
                        return;
                    }

                    //System.out.println(tableContent[attriIndex]+" "+operator + " " + operands[1]);
                    if(is_condition_valid(tableContent,attriIndex,operator,operands[1])) {    //if expression is not true then break
                        flag = true;
                        break;
                    }
                }

                if (!flag && !notContainsWhereClause) {    //if expression is false then print that line in new file
                    pw.println(line);
                    pw.flush();
                }
                else {
                    String[] contentArray=line.split("#");
                    String updatedString="";
                    for(int i=0;i<contentArray.length;i++){
                        if(indexValueMap.containsKey(i))
                            updatedString+=indexValueMap.get(i)+"#";
                        else
                            updatedString+=contentArray[i]+"#";
                    }
                    pw.println(updatedString);
                    pw.flush();
                    affectedRowsCount++;
                }
            }
            System.out.println(affectedRowsCount+" rows affected");
        }

        pw.close();     //close all files
        br.close();

        tableFile.delete();     //delete original table file
        tempFile.renameTo(tableFile);      //rename temp file to table file

	}

    public static void main(String[] args) throws IOException {

        Scanner sc=new Scanner(System.in);
        String query;
        while(true){
            query=sc.nextLine();
            if(query.isEmpty())
                continue;
            query=query.toLowerCase().trim();
            if(query.charAt(query.length()-1)!=';'){
                System.out.println("; missing");
                continue;
            }

            if(!satisfies_brackets(query)){
                System.out.println("Brackets not satisfied");
                return;
            }

            query=query.substring(0,query.length()-1);      //removing semi-colon from query
            String[] tokens=query.split(" ");
            if(tokens[0].equals("quit")){
                break;
            }
            else if(tokens[0].equals("create") && tokens[1].equals("table")){
                create_table(tokens,query);
            }
            else if(tokens.length>=4 && tokens[0].equals("insert") && tokens[1].equals("into")){
                try {
                    insert_entries(tokens,query);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if(tokens[0].equals("describe")){
                try {
                    describe(tokens);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if(query.equals("help tables")){
                try {
                    help_tables();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if(tokens[0].equals("help")){
                help_command(tokens);
            }
            else if(tokens.length==3 && tokens[0].equals("drop") && tokens[1].equals("table")){
                try{
                    drop_table(tokens);
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
            else if(tokens[0].equals("delete") && tokens[1].equals("from")){
                delete_rows(query,tokens);
            }
            else if(tokens[0].equals("select")){
                select_query(query);
            }
            else if(tokens.length>3 && tokens[0].equals("update") && tokens[2].equals("set")){
                update_rows(query,tokens);
            }
            else if(tokens[0].equals("truncate") && tokens[1].equals("table") && tokens.length==3){
                truncate(query,tokens);
            }
            else{
                System.out.println("Invalid Query");
            }
        }
    }
}
